package com.sekwah.advancedportals.core.portal;

import com.google.inject.Inject;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.permissions.Permissions;
import com.sekwah.advancedportals.core.registry.TagRegistry;
import com.sekwah.advancedportals.core.registry.TagTarget;
import com.sekwah.advancedportals.core.repository.ConfigRepository;
import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import com.sekwah.advancedportals.core.serializeddata.DataTag;
import com.sekwah.advancedportals.core.serializeddata.PlayerData;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.services.PlayerDataServices;
import com.sekwah.advancedportals.core.tags.TriggerBlockTag;
import com.sekwah.advancedportals.core.util.Lang;
import com.sekwah.advancedportals.core.warphandler.ActivationData;
import com.sekwah.advancedportals.core.warphandler.Tag;
import com.sekwah.advancedportals.core.warphandler.TriggerType;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * @author sekwah41
 */
public class AdvancedPortal implements TagTarget {
    @Inject
    private transient TagRegistry tagRegistry;

    private volatile BlockLocation maxLoc;

    private volatile BlockLocation minLoc;

    private final HashMap<String, String[]> args = new HashMap<>();

    /**
     * Sorted snapshot of the tags. It is replaced as a whole (never mutated)
     * so that region threads (Folia) can activate a portal while an admin is
     * editing it without seeing a half built list.
     */
    private transient volatile List<DataTag> portalTags = Collections.emptyList();

    private transient volatile boolean isSorted = false;

    @Inject
    private transient PlayerDataServices playerDataServices;

    @Inject
    private transient ConfigRepository configRepository;

    public AdvancedPortal() {
        this.minLoc = new BlockLocation();
        this.maxLoc = new BlockLocation();
    }

    public AdvancedPortal(BlockLocation minLoc, BlockLocation maxLoc,
                          TagRegistry tagRegistry,
                          PlayerDataServices playerDataServices) {
        this.tagRegistry = tagRegistry;
        this.playerDataServices = playerDataServices;
        this.updateBounds(minLoc, maxLoc);
    }

    public BlockLocation getMaxLoc() {
        return this.maxLoc;
    }

    public BlockLocation getMinLoc() {
        return this.minLoc;
    }

    @Override
    public synchronized String[] getArgValues(String argName) {
        return this.args.get(argName);
    }

    @Override
    public synchronized void setArgValues(String argName, String[] argValues) {
        this.isSorted = false;
        this.args.put(argName, argValues);
    }

    @Override
    public synchronized void addArg(String argName, String argValues) {
        this.isSorted = false;
        String[] existing = this.args.get(argName);
        if (existing == null) {
            this.args.put(argName, new String[] {argValues});
            return;
        }
        String[] updated = Arrays.copyOf(existing, existing.length + 1);
        updated[existing.length] = argValues;
        this.args.put(argName, updated);
    }

    public void updatePortalTagList() {
        List<DataTag> newTags;
        synchronized (this) {
            newTags = new ArrayList<>(this.args.size());
            for (Map.Entry<String, String[]> entry : args.entrySet()) {
                newTags.add(new DataTag(entry.getKey(), entry.getValue()));
            }
        }

        // sort the tags by priority
        newTags.sort(Comparator.comparingInt(o -> {
            Tag tag = tagRegistry.getTag(o.NAME);
            if (tag instanceof Tag.OrderPriority) {
                Tag.OrderPriority tagPriority = (Tag.OrderPriority) tag;
                return tagPriority.getPriority().ordinal();
            } else {
                return Tag.Priority.NORMAL.ordinal();
            }
        }));
        this.portalTags = newTags;
        this.isSorted = true;
    }

    @Override
    public synchronized void removeArg(String arg) {
        this.isSorted = false;
        this.args.remove(arg);
    }

    /**
     * Updates the bounds of the portal based on the provided locations.
     *
     * @param loc1 The first location.
     * @param loc2 The second location.
     */
    public synchronized void updateBounds(BlockLocation loc1,
                                          BlockLocation loc2) {
        int minX = Math.min(loc1.getPosX(), loc2.getPosX());
        int minY = Math.min(loc1.getPosY(), loc2.getPosY());
        int minZ = Math.min(loc1.getPosZ(), loc2.getPosZ());

        int maxX = Math.max(loc1.getPosX(), loc2.getPosX());
        int maxY = Math.max(loc1.getPosY(), loc2.getPosY());
        int maxZ = Math.max(loc1.getPosZ(), loc2.getPosZ());

        // Both corners always share the world of the first location. (The old
        // code could store two different worlds if the corners disagreed.)
        BlockLocation newMin =
            new BlockLocation(loc1.getWorldName(), minX, minY, minZ);
        BlockLocation newMax =
            new BlockLocation(loc1.getWorldName(), maxX, maxY, maxZ);
        this.minLoc = newMin;
        this.maxLoc = newMax;
    }

    /**
     * @param player        The player on the server attempting to use an
     *     advanced
     *                      portal
     * @param triggerType   The type of trigger that activated the portal
     * @return Whether the portal was successfully activated
     */
    public ActivationResult activate(PlayerContainer player,
                                     TriggerType triggerType) {
        if (!isSorted) {
            updatePortalTagList();
        }
        // Local snapshot so edits mid activation can't change what we iterate
        final List<DataTag> portalTags = this.portalTags;

        PlayerData playerData = playerDataServices.getPlayerData(player);

        if (!Permissions.USE_PORTAL.hasPermission(player)) {
            player.sendMessage(Lang.translate("portal.error.nopermission"));
            return ActivationResult.FAILED_DO_KNOCKBACK;
        }

        if (playerData.hasJoinCooldown()) {
            int cooldown =
                (int) Math.ceil(playerData.getJoinCooldownLeft() / 1000D);
            player.sendMessage(Lang.translateInsertVariables(
                "portal.cooldown.join", cooldown,
                Lang.translate(cooldown == 1 ? "time.second"
                                             : "time.seconds")));
            if (configRepository.playFailSound()) {
                Random rand = new Random();
                player.playSound("block.portal.travel", 0.05f,
                                 rand.nextFloat() * 0.4F + 0.8F);
            }
            return ActivationResult.FAILED_DO_KNOCKBACK;
        }

        ActivationData data = new ActivationData(triggerType);

        for (DataTag portalTag : portalTags) {
            Tag.Activation activationHandler = tagRegistry.getActivationHandler(
                portalTag.NAME, Tag.TagType.PORTAL);
            if (activationHandler != null) {
                boolean preActivated = activationHandler.preActivated(
                    this, player, data, this.getArgValues(portalTag.NAME));

                if (!preActivated) {
                    if (activationHandler instanceof Tag.DenyBehavior) {
                        Tag.DenyBehavior denyBehaviorHandler =
                            (Tag.DenyBehavior) activationHandler;
                        if (denyBehaviorHandler.getDenyBehavior().equals(
                                Tag.DenyBehavior.Behaviour.SILENT)) {
                            return ActivationResult.FAILED_DO_NOTHING;
                        }
                    }
                    return ActivationResult.FAILED_DO_KNOCKBACK;
                }
            }
        }
        for (DataTag portalTag : portalTags) {
            Tag.Activation activationHandler = tagRegistry.getActivationHandler(
                portalTag.NAME, Tag.TagType.PORTAL);
            if (activationHandler != null
                && !activationHandler.activated(
                    this, player, data, this.getArgValues(portalTag.NAME))) {
                return ActivationResult.FAILED_DO_KNOCKBACK;
            }
        }
        // Post activation (messages, commands, charging costs...) should
        // happen once the player has actually arrived, which on Paper/Folia
        // is after the asynchronous teleport completes. If the teleport failed
        // (e.g. another plugin cancelled it) nothing is charged or run.
        final Runnable postActivation = () -> {
            for (DataTag portalTag : portalTags) {
                Tag.Activation activationHandler =
                    tagRegistry.getActivationHandler(portalTag.NAME,
                                                     Tag.TagType.PORTAL);
                if (activationHandler != null) {
                    activationHandler.postActivated(
                        this, player, data, this.getArgValues(portalTag.NAME));
                }
            }
        };
        final CompletableFuture<Boolean> teleportFuture =
            data.getTeleportFuture();
        if (teleportFuture == null) {
            postActivation.run();
        } else if (teleportFuture.isDone()) {
            if (Boolean.TRUE.equals(teleportFuture.getNow(false))) {
                postActivation.run();
            }
        } else {
            teleportFuture.thenAccept(success -> {
                if (success) {
                    postActivation.run();
                }
            });
        }
        if (data.hasActivated()) {
            playerData.setPortalBlockCooldown(1000);
            playerData.setInPortal(this.getName());
            return ActivationResult.SUCCESS;
        }
        return ActivationResult.FAILED_DO_KNOCKBACK;
    }

    public boolean isLocationInPortal(BlockLocation loc) {
        return this.isLocationInPortal(loc, 0);
    }

    public boolean isLocationInPortal(PlayerLocation loc) {
        return this.isLocationInPortal(loc.toBlockPos(), 0);
    }

    public boolean isLocationInPortal(PlayerLocation loc, int additionalArea) {
        return this.isLocationInPortal(loc.toBlockPos(), additionalArea);
    }

    public boolean isLocationInPortal(BlockLocation loc, int additionalArea) {
        return this.isLocationInPortal(loc.getWorldName(), loc.getPosX(),
                                       loc.getPosY(), loc.getPosZ(),
                                       additionalArea);
    }

    /**
     * Allocation free bounds check, used by the hot paths (movement and block
     * protection events).
     */
    public boolean isLocationInPortal(String worldName, int x, int y, int z,
                                      int additionalArea) {
        // Read once so a concurrent updateBounds can't give us a mixed box
        final BlockLocation min = this.minLoc;
        final BlockLocation max = this.maxLoc;

        return x >= min.getPosX() - additionalArea
            && x < max.getPosX() + 1 + additionalArea
            && y >= min.getPosY() - additionalArea
            && y < max.getPosY() + 1 + additionalArea
            && z >= min.getPosZ() - additionalArea
            && z < max.getPosZ() + 1 + additionalArea
            && Objects.equals(worldName, min.getWorldName());
    }

    public void setArgValues(DataTag portalTag) {
        this.setArgValues(portalTag.NAME, portalTag.VALUES);
    }

    @Override
    public synchronized ArrayList<DataTag> getArgs() {
        ArrayList<DataTag> tagList = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : this.args.entrySet()) {
            tagList.add(new DataTag(entry.getKey(), entry.getValue()));
        }
        return tagList;
    }

    public boolean isTriggerBlock(String blockMaterial) {
        if (blockMaterial == null) {
            return false;
        }
        String[] triggerBlocks = this.getArgValues(TriggerBlockTag.TAG_NAME);
        if (triggerBlocks != null) {
            for (String triggerBlock : triggerBlocks) {
                if (blockMaterial.equalsIgnoreCase(triggerBlock)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String getName() {
        String[] name = getArgValues("name");
        return name == null || name.length == 0 ? null : name[0];
    }

    /**
     * @return the world this portal is in
     */
    public String getWorldName() {
        return this.minLoc.getWorldName();
    }

    @Override
    public Tag.TagType targetTagType() {
        return Tag.TagType.PORTAL;
    }

    public boolean isChunkOverlapping(int chunkX, int chunkZ) {
        int chunkMinX = chunkX * 16;
        int chunkMinZ = chunkZ * 16;
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;

        int portalMinX = minLoc.getPosX();
        int portalMaxX = maxLoc.getPosX();
        int portalMinZ = minLoc.getPosZ();
        int portalMaxZ = maxLoc.getPosZ();

        return !(portalMaxX < chunkMinX || portalMinX > chunkMaxX ||
                portalMaxZ < chunkMinZ || portalMinZ > chunkMaxZ);
    }
}
