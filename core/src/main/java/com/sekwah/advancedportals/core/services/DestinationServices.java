package com.sekwah.advancedportals.core.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.destination.Destination;
import com.sekwah.advancedportals.core.effect.WarpEffect;
import com.sekwah.advancedportals.core.registry.TagRegistry;
import com.sekwah.advancedportals.core.registry.WarpEffectRegistry;
import com.sekwah.advancedportals.core.repository.ConfigRepository;
import com.sekwah.advancedportals.core.repository.IDestinationRepository;
import com.sekwah.advancedportals.core.serializeddata.DataTag;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.tags.NameTag;
import com.sekwah.advancedportals.core.util.Lang;
import com.sekwah.advancedportals.core.warphandler.Tag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class DestinationServices {
    @Inject
    private IDestinationRepository destinationRepository;

    @Inject
    private WarpEffectRegistry warpEffectRegistry;

    @Inject
    private ConfigRepository configRepository;

    @Inject
    TagRegistry tagRegistry;

    @Inject
    private PortalServices portalServices;

    /**
     * Read from every region thread on Folia (every warp looks one up).
     */
    private final Map<String, Destination> destinationCache =
        new ConcurrentHashMap<>();

    /**
     * Names come from the cache instead of listing the folder on disk, as
     * this is used for tab completion.
     */
    public List<String> getDestinationNames() {
        return new ArrayList<>(destinationCache.keySet());
    }

    public List<Destination> getDestinations() {
        return new ArrayList<>(destinationCache.values());
    }

    public void loadDestinations() {
        List<String> destinationNames = destinationRepository.getAllNames();
        Map<String, Destination> loaded = new HashMap<>();
        for (String name : destinationNames) {
            Destination destination = destinationRepository.get(name);
            if (destination == null) {
                continue;
            }
            // The file name is the source of truth for the name
            destination.setArgValues(NameTag.TAG_NAME, new String[] {name});
            loaded.put(name, destination);
        }
        destinationCache.clear();
        destinationCache.putAll(loaded);
    }

    public Destination createDesti(PlayerLocation playerLocation,
                                   List<DataTag> tags) {
        return createDesti(null, playerLocation, tags);
    }

    public Destination createDesti(PlayerContainer player,
                                   PlayerLocation playerLocation,
                                   List<DataTag> tags) {
        try {
            // Find the tag with the "name" NAME
            DataTag nameTag = tags.stream()
                                  .filter(tag -> tag.NAME.equals("name"))
                                  .findFirst()
                                  .orElse(null);

            String name = nameTag == null ? null : nameTag.VALUES[0];

            // If the name is null, send an error saying that the name is
            // required.
            if (nameTag == null) {
                if (player != null)
                    player.sendMessage(Lang.getNegativePrefix()
                                       + Lang.translate("desti.error.noname"));
                return null;
            }

            if (name == null || name.isEmpty()) {
                if (player != null)
                    player.sendMessage(
                        Lang.getNegativePrefix()
                        + Lang.translate("command.error.noname"));
                return null;
            } else if (this.destinationRepository.containsKey(name)
                       || getDestination(name) != null) {
                if (player != null)
                    player.sendMessage(Lang.getNegativePrefix()
                                       + Lang.translateInsertVariables(
                                           "command.error.nametaken", name));
                return null;
            }

            Destination desti = new Destination(playerLocation);
            for (DataTag portalTag : tags) {
                desti.setArgValues(portalTag);
            }
            for (DataTag destiTag : tags) {
                Tag.Creation creation =
                    tagRegistry.getCreationHandler(destiTag.NAME);
                if (creation != null) {
                    if (!creation.created(desti, player, destiTag.VALUES)) {
                        return null;
                    }
                }
            }
            if (this.destinationRepository.save(name, desti)) {
                this.destinationCache.put(name, desti);
            } else {
                return null;
            }
            return desti;
        } catch (IllegalArgumentException e) {
            if (player != null) {
                player.sendMessage(
                    Lang.getNegativePrefix()
                    + Lang.translate("command.error.invalidname"));
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            if (player != null) {
                player.sendMessage(Lang.getNegativePrefix()
                                   + Lang.translate("desti.error.save"));
            }
            return null;
        }
    }

    public boolean removeDestination(String name,
                                     PlayerContainer playerContainer) {
        try {
            Destination destination = getDestination(name);
            if (destination == null) {
                return false;
            }
            String key = destination.getName();

            if (this.destinationRepository.containsKey(key)
                && !this.destinationRepository.delete(key)) {
                return false;
            }
            this.destinationCache.remove(key);

            for (DataTag tag : destination.getArgs()) {
                Tag.Creation creation = tagRegistry.getCreationHandler(tag.NAME);
                if (creation != null) {
                    creation.destroyed(destination, playerContainer,
                                       tag.VALUES);
                }
            }
            return true;
        } catch (IllegalArgumentException e) {
            if (playerContainer != null) {
                playerContainer.sendMessage(
                    Lang.getNegativePrefix()
                    + Lang.translate("command.error.invalidname"));
            }
            return false;
        }
    }

    /**
     * @return the destination with that name, falling back to a case
     *     insensitive match.
     */
    public Destination getDestination(String name) {
        if (name == null) {
            return null;
        }
        Destination destination = destinationCache.get(name);
        if (destination != null) {
            return destination;
        }
        for (Map.Entry<String, Destination> entry :
             destinationCache.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public boolean teleportToDestination(String name,
                                         PlayerContainer playerContainer) {
        return teleportToDestination(name, playerContainer, false);
    }

    public boolean teleportToDestination(String name, PlayerContainer player,
                                         boolean doEffect) {
        if (player == null) {
            return false;
        }
        Destination destination = getDestination(name);
        if (destination == null) {
            return false;
        }
        String worldName = destination.getLoc().getWorldName();
        if (player.getServer().getWorld(worldName) == null) {
            player.sendMessage(Lang.getNegativePrefix()
                               + Lang.translateInsertVariables(
                                   "desti.error.invalidworld",
                                   destination.getName(), worldName));
            return false;
        }

        CompletableFuture<Boolean> teleport =
            player.teleportAsync(destination.getLoc());

        if (doEffect && configRepository.getWarpEffectEnabled()) {
            // Played at the destination, so once the teleport has finished
            teleport.thenAccept(success -> {
                if (!success) {
                    return;
                }
                WarpEffect.Visual warpEffectVisual =
                    warpEffectRegistry.getVisualEffect(
                        configRepository.getWarpVisual());
                if (warpEffectVisual != null) {
                    warpEffectVisual.onWarpVisual(player,
                                                  WarpEffect.Action.ENTER);
                }
                WarpEffect.Sound warpEffectSound =
                    warpEffectRegistry.getSoundEffect(
                        configRepository.getWarpSound());
                if (warpEffectSound != null) {
                    warpEffectSound.onWarpSound(player,
                                                WarpEffect.Action.ENTER);
                }
            });
        }
        // Already completed on synchronous platforms. On Paper/Folia the
        // teleport is in flight, the commands only report that it was started
        // successfully (a cancelled teleport is reported by the platform).
        return !teleport.isDone() || Boolean.TRUE.equals(teleport.getNow(false));
    }

    public boolean renameDestination(String oldName, String newName,
                                     PlayerContainer player) {
        try {
            Destination destination = getDestination(oldName);
            // Check if the old destination exists
            if (destination == null) {
                if (player != null) {
                    player.sendMessage(
                        Lang.getNegativePrefix()
                        + Lang.translateInsertVariables(
                            "command.error.destination.notfound", oldName));
                }
                return false;
            }
            String key = destination.getName();

            if (newName == null || newName.isEmpty() || newName.contains(" ")) {
                if (player != null) {
                    player.sendMessage(
                        Lang.getNegativePrefix()
                        + Lang.translate("tag.name.error.nospaces"));
                }
                return false;
            }

            // Check if the new name is already taken
            if (!key.equalsIgnoreCase(newName)
                && (destinationRepository.containsKey(newName)
                    || getDestination(newName) != null)) {
                if (player != null) {
                    player.sendMessage(Lang.getNegativePrefix()
                                       + Lang.translateInsertVariables(
                                           "command.error.nametaken", newName));
                }
                return false;
            }

            // Keep the name tag in sync, it used to keep the old name
            destination.setArgValues(NameTag.TAG_NAME,
                                     new String[] {newName});

            // Save first so a failed save can't lose the destination
            if (!destinationRepository.save(newName, destination)) {
                destination.setArgValues(NameTag.TAG_NAME, new String[] {key});
                if (player != null) {
                    player.sendMessage(
                        Lang.getNegativePrefix()
                        + Lang.translate("command.destination.rename.error"));
                }
                return false;
            }
            if (!key.equals(newName)) {
                destinationRepository.delete(key);
                destinationCache.remove(key);
            }
            destinationCache.put(newName, destination);

            // Portals pointing at the old name would otherwise break silently
            int updatedPortals =
                portalServices.replaceDestinationReferences(key, newName);

            if (player != null) {
                player.sendMessage(Lang.getPositivePrefix()
                                   + Lang.translateInsertVariables(
                                       "command.destination.rename.success",
                                       key, newName));
                if (updatedPortals > 0) {
                    player.sendMessage(
                        Lang.getPositivePrefix()
                        + Lang.translateInsertVariables(
                            "command.destination.rename.portals",
                            updatedPortals));
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

    /**
     * Change the location of an existing destination and persist the change.
     *
     * @param name   The name of the destination.
     * @param newLoc The new PlayerLocation.
     * @param player The player requesting the change (for feedback).
     * @return if the destination was moved and saved
     */
    public boolean changeDestinationLocation(String name,
                                             PlayerLocation newLoc,
                                             PlayerContainer player) {
        Destination destination = getDestination(name);
        if (destination == null) {
            if (player != null) {
                player.sendMessage(Lang.getNegativePrefix()
                                   + Lang.translateInsertVariables(
                                       "command.destination.notfound", name));
            }
            return false;
        }
        PlayerLocation oldLoc = destination.getLoc();
        destination.setLoc(newLoc);
        if (saveDestination(destination)) {
            if (player != null) {
                player.sendMessage(Lang.getPositivePrefix()
                                   + Lang.translateInsertVariables(
                                       "command.destination.moved",
                                       destination.getName()));
            }
            return true;
        }
        destination.setLoc(oldLoc);
        if (player != null) {
            player.sendMessage(Lang.getNegativePrefix()
                               + Lang.translate("command.destination.move.error"));
        }
        return false;
    }

    /**
     * Writes the current state of a destination that is registered back to
     * disk. Call after changing its tags or location.
     */
    public boolean saveDestination(Destination destination) {
        String name = destination.getName();
        if (name == null) {
            return false;
        }
        try {
            return destinationRepository.save(name, destination);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
