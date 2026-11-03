package com.sekwah.advancedportals.folia.connector.container;

import com.sekwah.advancedportals.core.AdvancedPortalsCore;
import com.sekwah.advancedportals.core.connector.containers.EntityContainer;
import com.sekwah.advancedportals.core.connector.containers.TeleportOption;
import com.sekwah.advancedportals.core.connector.containers.WorldContainer;
import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.serializeddata.Vector;
import com.sekwah.advancedportals.shadowed.inject.Inject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;
import io.papermc.paper.entity.TeleportFlag;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Just a temporary container for whenever advanced portals needs to get data
 * from a player
 */
public class FoliaEntityContainer implements EntityContainer {
    @Inject
    private AdvancedPortalsCore portalsCore;

    private final Entity entity;

    public FoliaEntityContainer(Entity entity) {
        this.entity = entity;
    }

    @Override
    public PlayerLocation getLoc() {
        Location loc = this.entity.getLocation();
        return new PlayerLocation(loc.getWorld().getName(), loc.getX(),
                                  loc.getY(), loc.getZ(), loc.getYaw(),
                                  loc.getPitch());
    }

    @Override
    public BlockLocation getBlockLoc() {
        Location loc = this.entity.getLocation();
        return new BlockLocation(loc.getWorld().getName(), loc.getBlockX(),
                                 loc.getBlockY(), loc.getBlockZ());
    }

    @Override
    public double getHeight() {
        return this.entity.getHeight();
    }

    /**
     * Folia/Paper teleports are asynchronous (the destination chunk may have
     * to be loaded and the entity may change region), so the returned future
     * only completes once the entity has really arrived.
     */
    @Override
    public CompletableFuture<Boolean> teleportAsync(PlayerLocation location) {
        World world = Bukkit.getWorld(location.getWorldName());
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }
        return this.entity
            .teleportAsync(new Location(world, location.getPosX(),
                                        location.getPosY(), location.getPosZ(),
                                        location.getYaw(), location.getPitch()),
                           PlayerTeleportEvent.TeleportCause.PLUGIN)
            .exceptionally(throwable -> false);
    }

    /**
     * @return true if the teleport was started. Use {@link #teleportAsync} to
     *     find out if it finished.
     */
    @Override
    public double getWidth() {
        return this.entity.getWidth();
    }

    /**
     * Same as {@link #teleportAsync(PlayerLocation)} but keeps the momentum of
     * the entity when {@link TeleportOption#KEEP_VELOCITY} is set.
     */
    @Override
    public CompletableFuture<Boolean> teleportAsync(
        PlayerLocation location, Set<TeleportOption> options) {
        if (!options.contains(TeleportOption.KEEP_VELOCITY)) {
            return this.teleportAsync(location);
        }
        World world = Bukkit.getWorld(location.getWorldName());
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }
        return this.entity
            .teleportAsync(new Location(world, location.getPosX(),
                                        location.getPosY(), location.getPosZ(),
                                        location.getYaw(), location.getPitch()),
                           PlayerTeleportEvent.TeleportCause.PLUGIN,
                           TeleportFlag.Relative.VELOCITY_X,
                           TeleportFlag.Relative.VELOCITY_Y,
                           TeleportFlag.Relative.VELOCITY_Z,
                           TeleportFlag.Relative.VELOCITY_ROTATION)
            .exceptionally(throwable -> false);
    }

    @Override
    public boolean teleport(PlayerLocation location) {
        if (Bukkit.getWorld(location.getWorldName()) == null) {
            return false;
        }
        this.teleportAsync(location);
        return true;
    }

    @Override
    public WorldContainer getWorld() {
        return new FoliaWorldContainer(this.entity.getWorld());
    }

    @Override
    public String getName() {
        return this.entity.getName();
    }

    @Override
    public String getWorldName() {
        return this.entity.getWorld().getName();
    }

    @Override
    public void setVelocity(Vector vector) {
        this.entity.setVelocity(new org.bukkit.util.Vector(
            vector.getX(), vector.getY(), vector.getZ()));
    }
}
