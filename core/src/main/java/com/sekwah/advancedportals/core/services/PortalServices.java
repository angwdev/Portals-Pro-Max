package com.sekwah.advancedportals.core.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sekwah.advancedportals.core.connector.containers.GameMode;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.connector.containers.WorldContainer;
import com.sekwah.advancedportals.core.portal.AdvancedPortal;
import com.sekwah.advancedportals.core.portal.PortalIndex;
import com.sekwah.advancedportals.core.registry.TagRegistry;
import com.sekwah.advancedportals.core.repository.ConfigRepository;
import com.sekwah.advancedportals.core.repository.IPortalRepository;
import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import com.sekwah.advancedportals.core.serializeddata.DataTag;
import com.sekwah.advancedportals.core.serializeddata.PlayerData;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.tags.DestiTag;
import com.sekwah.advancedportals.core.tags.NameTag;
import com.sekwah.advancedportals.core.util.Lang;
import com.sekwah.advancedportals.core.util.PlayerUtils;
import com.sekwah.advancedportals.core.warphandler.Tag;
import com.sekwah.advancedportals.core.warphandler.TriggerType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

@Singleton
public class PortalServices {
    /**
     * Small amount removed from the top of the players hitbox so standing
     * exactly on a block boundary doesn't count as touching the block above.
     */
    private static final double HITBOX_EPSILON = 1.0E-7;

    @Inject
    private IPortalRepository portalRepository;

    @Inject
    private transient PlayerDataServices playerDataServices;

    @Inject
    private ConfigRepository configRepository;

    /**
     * Portals are read from every region thread on Folia so this needs to be
     * concurrent. (Writes only happen from commands and reloads)
     */
    private final Map<String, AdvancedPortal> portalCache =
        new ConcurrentHashMap<>();

    /**
     * Lock free lookup structure that is rebuilt whenever the cache changes.
     */
    private volatile PortalIndex index = PortalIndex.EMPTY;

    @Inject
    TagRegistry tagRegistry;

    public void loadPortals() {
        List<String> portalNames = portalRepository.getAllNames();
        Map<String, AdvancedPortal> loaded = new HashMap<>();
        for (String name : portalNames) {
            AdvancedPortal portal = portalRepository.get(name);
            if (portal == null) {
                continue;
            }
            // The file name is the source of truth for the name, otherwise a
            // file that was renamed by hand would no longer match its tag.
            portal.setArgValues(NameTag.TAG_NAME, new String[] {name});
            portal.updateBounds(portal.getMinLoc(), portal.getMaxLoc());
            loaded.put(name, portal);
        }
        portalCache.clear();
        portalCache.putAll(loaded);
        rebuildIndex();
    }

    private synchronized void rebuildIndex() {
        this.index = PortalIndex.build(this.portalCache.values());
    }

    /**
     * @return the index for plugins/listeners that want to do their own cheap
     *     lookups. Never null.
     */
    public PortalIndex getIndex() {
        return this.index;
    }

    public boolean inPortalRegionProtected(BlockLocation loc) {
        return this.index.findContaining(
                   loc.getWorldName(), loc.getPosX(), loc.getPosY(),
                   loc.getPosZ(), configRepository.getProtectionRadius())
            != null;
    }

    /**
     * Allocation free variant for the very frequent block events.
     */
    public boolean inPortalRegionProtected(String worldName, int x, int y,
                                           int z) {
        return this.index.findContaining(worldName, x, y, z,
                                         configRepository.getProtectionRadius())
            != null;
    }

    public boolean inPortalRegionProtected(PlayerLocation loc) {
        return inPortalRegionProtected(loc.toBlockPos());
    }

    public boolean inPortalRegion(BlockLocation loc, int extraBlocks) {
        return inPortalRegionGetName(loc, extraBlocks) != null;
    }

    public String inPortalRegionGetName(PlayerLocation loc, int extraBlocks) {
        return inPortalRegionGetName(loc.toBlockPos(), extraBlocks);
    }

    public String inPortalRegionGetName(BlockLocation loc) {
        return inPortalRegionGetName(loc, 0);
    }

    /**
     * Checks every block the entities hitbox covers vertically (feet to head)
     * instead of only the feet and one other block.
     *
     * @param loc exact position of the entities feet
     * @param height height of the entities hitbox
     * @param extraBlocks how many blocks to expand each portal by
     * @return name of the first portal touched or null
     */
    public String inPortalRegionGetName(PlayerLocation loc, double height,
                                        int extraBlocks) {
        int x = (int) Math.floor(loc.getPosX());
        int z = (int) Math.floor(loc.getPosZ());
        int feetY = (int) Math.floor(loc.getPosY());
        int topY = Math.max(
            feetY,
            (int) Math.floor(loc.getPosY() + height - HITBOX_EPSILON));
        for (int y = feetY; y <= topY; y++) {
            AdvancedPortal portal = this.index.findContaining(
                loc.getWorldName(), x, y, z, extraBlocks);
            if (portal != null) {
                return portal.getName();
            }
        }
        return null;
    }

    public String inPortalRegionGetName(BlockLocation loc, int extraBlocks) {
        AdvancedPortal portal = this.index.findContaining(
            loc.getWorldName(), loc.getPosX(), loc.getPosY(), loc.getPosZ(),
            extraBlocks);
        return portal == null ? null : portal.getName();
    }

    /**
     * @return the portal with that name. Falls back to a case insensitive
     *     match so players don't have to remember the exact casing.
     */
    @Nullable
    public AdvancedPortal getPortal(String portalName) {
        if (portalName == null) {
            return null;
        }
        AdvancedPortal portal = portalCache.get(portalName);
        if (portal != null) {
            return portal;
        }
        for (Map.Entry<String, AdvancedPortal> entry : portalCache.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(portalName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Resolves the real (stored) name of a portal, ignoring casing.
     */
    @Nullable
    public String getPortalKey(String portalName) {
        AdvancedPortal portal = getPortal(portalName);
        return portal == null ? null : portal.getName();
    }

    public enum PortalActivationResult {
        NOT_IN_PORTAL,
        PORTAL_TELEPORTED,
        PORTAL_ACTIVATED,
        PORTAL_DENIED
    }

    public PortalActivationResult checkPortalActivation(
        PlayerContainer player, PlayerLocation toLoc, TriggerType triggerType) {
        return checkPortalActivation(player, null, toLoc, triggerType);
    }

    /**
     * @param fromLoc where the player came from, can be null. Used to knock
     *     the player back the way they came instead of just backwards from
     *     where they are looking.
     */
    public PortalActivationResult checkPortalActivation(
        PlayerContainer player, @Nullable PlayerLocation fromLoc,
        PlayerLocation toLoc, TriggerType triggerType) {
        final String worldName = toLoc.getWorldName();
        final int x = (int) Math.floor(toLoc.getPosX());
        final int z = (int) Math.floor(toLoc.getPosZ());

        // Fast path for the overwhelming majority of movement: there is no
        // portal anywhere in this chunk so there is no need to touch the world
        // or the players data beyond clearing their "in portal" state.
        final AdvancedPortal[] candidates =
            this.index.inChunkOf(worldName, x, z);

        PlayerData playerData = playerDataServices.getPlayerData(player);

        if (candidates.length == 0) {
            if (playerData.inPortal() != null) {
                playerData.setNotInPortal();
            }
            return PortalActivationResult.NOT_IN_PORTAL;
        }

        if (configRepository.blockSpectatorMode()
            && player.getGameMode() == GameMode.SPECTATOR) {
            return PortalActivationResult.NOT_IN_PORTAL;
        }

        // The players hitbox can touch up to 3 blocks vertically. Previously
        // only the feet block and (feet block + floor(height)) were checked
        // which looked at the wrong block for the head whenever the player
        // wasn't standing exactly on a block boundary.
        final double height = player.getHeight();
        final int feetY = (int) Math.floor(toLoc.getPosY());
        final int topY = Math.max(
            feetY,
            (int) Math.floor(toLoc.getPosY() + height - HITBOX_EPSILON));
        final int levels = topY - feetY + 1;

        WorldContainer world = null;
        String[] materials = null;
        byte[] waterlogged = null;

        for (AdvancedPortal portal : candidates) {
            boolean triggered = false;
            boolean checkWaterLogged = portal.isTriggerBlock("water");

            for (int i = 0; i < levels && !triggered; i++) {
                int y = feetY + i;
                if (!portal.isLocationInPortal(worldName, x, y, z, 0)) {
                    continue;
                }

                // Only now do we need to look at the world
                if (world == null) {
                    world = player.getWorld();
                    materials = new String[levels];
                    waterlogged = new byte[levels];
                }
                if (materials[i] == null) {
                    BlockLocation blockPos =
                        new BlockLocation(worldName, x, y, z);
                    materials[i] = world.getBlock(blockPos);
                    waterlogged[i] =
                        (byte) (world.isWaterlogged(blockPos) ? 2 : 1);
                }

                triggered = portal.isTriggerBlock(materials[i])
                    || (checkWaterLogged && waterlogged[i] == 2);
            }

            if (!triggered) {
                continue;
            }

            String portalName = portal.getName();
            if (Objects.equals(playerData.inPortal(), portalName)) {
                return PortalActivationResult.PORTAL_DENIED;
            }
            switch (portal.activate(player, triggerType)) {
                case SUCCESS:
                    playerData.setInPortal(portalName);
                    return PortalActivationResult.PORTAL_ACTIVATED;
                case FAILED_DO_KNOCKBACK:
                    playerData.setInPortal(portalName);
                    PlayerUtils.throwPlayerBack(
                        player, fromLoc, toLoc,
                        configRepository.getThrowbackStrength());
                    return PortalActivationResult.PORTAL_DENIED;
                default:
                    // FAILED_DO_NOTHING: e.g. a delayed portal, deliberately
                    // not marked as entered so the portal event can still
                    // activate it.
                    break;
            }
        }
        if (playerData.inPortal() != null) {
            playerData.setNotInPortal();
        }
        return PortalActivationResult.NOT_IN_PORTAL;
    }

    public List<String> getPortalNames() {
        return new ArrayList<>(portalCache.keySet());
    }

    public List<AdvancedPortal> getPortals() {
        return new ArrayList<>(portalCache.values());
    }

    /**
     * Removes the portal, runs the destroyed handlers of its tags and deletes
     * its file.
     *
     * @return true if the portal no longer exists after the call
     */
    public boolean removePortal(String name, @Nullable PlayerContainer player) {
        try {
            AdvancedPortal portal = getPortal(name);
            if (portal == null) {
                if (player != null) {
                    player.sendMessage(
                        Lang.getNegativePrefix()
                        + Lang.translateInsertVariables(
                            "command.portal.info.notfound", name));
                }
                return false;
            }
            String key = portal.getName();

            if (this.portalRepository.containsKey(key)
                && !this.portalRepository.delete(key)) {
                return false;
            }
            this.portalCache.remove(key);
            rebuildIndex();

            for (DataTag tag : portal.getArgs()) {
                Tag.Creation creation = tagRegistry.getCreationHandler(tag.NAME);
                if (creation != null) {
                    creation.destroyed(portal, player, tag.VALUES);
                }
            }
            return true;
        } catch (IllegalArgumentException e) {
            if (player != null) {
                player.sendMessage(
                    Lang.getNegativePrefix()
                    + Lang.translate("command.error.invalidname"));
            }
            return false;
        }
    }

    public AdvancedPortal createPortal(PlayerContainer player,
                                       ArrayList<DataTag> tags) {
        PlayerData tempData = playerDataServices.getPlayerData(player);

        if (tempData.getPos1() == null || tempData.getPos2() == null) {
            player.sendMessage(
                Lang.getNegativePrefix()
                + Lang.translate("portal.error.selection.missing"));
            return null;
        }

        if (!tempData.getPos1().getWorldName().equals(
                tempData.getPos2().getWorldName())) {
            player.sendMessage(
                Lang.getNegativePrefix()
                + Lang.translate("portal.error.selection.differentworlds"));
            return null;
        }

        return createPortal(player, tempData.getPos1(), tempData.getPos2(),
                            tags);
    }

    /**
     * @param player if null it is assumed an automation is creating the portal
     *     so checks for permissions are not needed
     * @param pos1
     * @param pos2
     * @param tags
     * @return
     */
    public AdvancedPortal createPortal(@Nullable PlayerContainer player,
                                       BlockLocation pos1, BlockLocation pos2,
                                       List<DataTag> tags) {
        try {
            // Find the tag with the "name" NAME
            DataTag nameTag =
                tags.stream()
                    .filter(tag -> tag.NAME.equals(NameTag.TAG_NAME))
                    .findFirst()
                    .orElse(null);

            String name = nameTag == null || nameTag.VALUES.length == 0
                ? null
                : nameTag.VALUES[0];
            if (nameTag == null || name == null || name.isEmpty()) {
                if (player != null)
                    player.sendMessage(
                        Lang.getNegativePrefix()
                        + Lang.translate("command.error.noname"));
                return null;
            } else if (this.portalRepository.containsKey(name)
                       || getPortal(name) != null) {
                if (player != null)
                    player.sendMessage(Lang.getNegativePrefix()
                                       + Lang.translateInsertVariables(
                                           "command.error.nametaken", name));
                return null;
            }

            AdvancedPortal portal =
                new AdvancedPortal(pos1, pos2, tagRegistry, playerDataServices);

            for (DataTag portalTag : tags) {
                portal.setArgValues(portalTag);
            }

            for (DataTag portalTag : tags) {
                Tag.Creation creation =
                    tagRegistry.getCreationHandler(portalTag.NAME);
                if (creation != null) {
                    if (!creation.created(portal, player, portalTag.VALUES)) {
                        return null;
                    }
                }
            }

            if (this.portalRepository.save(name, portal)) {
                this.portalCache.put(name, portal);
                rebuildIndex();
            } else {
                return null;
            }

            return portal;
        } catch (IllegalArgumentException e) {
            if (player != null) {
                player.sendMessage(
                    Lang.getNegativePrefix()
                    + Lang.translate("command.error.invalidname"));
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            if (player != null)
                player.sendMessage(Lang.getNegativePrefix()
                                   + Lang.translate("portal.error.save"));
            return null;
        }
    }

    /**
     * Writes the current state of a portal that is already registered back to
     * disk. Call this after changing its tags.
     *
     * @return if the portal was saved
     */
    public boolean savePortal(AdvancedPortal portal) {
        String name = portal.getName();
        if (name == null) {
            return false;
        }
        try {
            return this.portalRepository.save(name, portal);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Changes the region of an existing portal, saves it and updates the
     * lookup index so the new region is used straight away.
     */
    public boolean updatePortalRegion(AdvancedPortal portal, BlockLocation pos1,
                                      BlockLocation pos2) {
        BlockLocation oldMin = portal.getMinLoc();
        BlockLocation oldMax = portal.getMaxLoc();

        portal.updateBounds(pos1, pos2);
        if (!savePortal(portal)) {
            // Don't leave memory and disk disagreeing
            portal.updateBounds(oldMin, oldMax);
            return false;
        }
        rebuildIndex();
        return true;
    }

    /**
     * Renames a portal (file + name tag) keeping every other tag.
     */
    public boolean renamePortal(String oldName, String newName,
                                @Nullable PlayerContainer player) {
        try {
            AdvancedPortal portal = getPortal(oldName);
            if (portal == null) {
                if (player != null) {
                    player.sendMessage(
                        Lang.getNegativePrefix()
                        + Lang.translateInsertVariables(
                            "command.portal.info.notfound", oldName));
                }
                return false;
            }
            String key = portal.getName();

            if (newName == null || newName.isEmpty() || newName.contains(" ")) {
                if (player != null) {
                    player.sendMessage(
                        Lang.getNegativePrefix()
                        + Lang.translate("tag.name.error.nospaces"));
                }
                return false;
            }

            if (!key.equalsIgnoreCase(newName)
                && (portalRepository.containsKey(newName)
                    || getPortal(newName) != null)) {
                if (player != null) {
                    player.sendMessage(Lang.getNegativePrefix()
                                       + Lang.translateInsertVariables(
                                           "command.error.nametaken", newName));
                }
                return false;
            }

            portal.setArgValues(NameTag.TAG_NAME, new String[] {newName});
            if (!portalRepository.save(newName, portal)) {
                portal.setArgValues(NameTag.TAG_NAME, new String[] {key});
                return false;
            }
            if (!key.equals(newName)) {
                portalRepository.delete(key);
                portalCache.remove(key);
            }
            portalCache.put(newName, portal);
            rebuildIndex();
            return true;
        } catch (IllegalArgumentException e) {
            if (player != null) {
                player.sendMessage(
                    Lang.getNegativePrefix()
                    + Lang.translate("command.error.invalidname"));
            }
            return false;
        }
    }

    /**
     * When a destination is renamed, point every portal that used the old name
     * at the new one so they don't silently break.
     *
     * @return how many portals were changed
     */
    public int replaceDestinationReferences(String oldName, String newName) {
        int changed = 0;
        for (AdvancedPortal portal : portalCache.values()) {
            String[] destinations = portal.getArgValues(DestiTag.TAG_NAME);
            if (destinations == null) {
                continue;
            }
            boolean modified = false;
            String[] updated = destinations.clone();
            for (int i = 0; i < updated.length; i++) {
                if (updated[i].equals(oldName)) {
                    updated[i] = newName;
                    modified = true;
                }
            }
            if (modified) {
                portal.setArgValues(DestiTag.TAG_NAME, updated);
                if (savePortal(portal)) {
                    changed++;
                }
            }
        }
        return changed;
    }

    /**
     * @return names of all portals that use a destination.
     */
    public List<String> getPortalsUsingDestination(String destinationName) {
        List<String> names = new ArrayList<>();
        for (AdvancedPortal portal : portalCache.values()) {
            String[] destinations = portal.getArgValues(DestiTag.TAG_NAME);
            if (destinations != null
                && Arrays.asList(destinations).contains(destinationName)) {
                names.add(portal.getName());
            }
        }
        return names;
    }

    public boolean removePlayerSelection(PlayerContainer player) {
        return false;
    }
}
