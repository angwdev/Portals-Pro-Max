package com.sekwah.advancedportals.core.connector.containers;

import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.serializeddata.Vector;
import java.util.concurrent.CompletableFuture;

public interface EntityContainer {
    PlayerLocation getLoc();

    double getHeight();

    BlockLocation getBlockLoc();

    boolean teleport(PlayerLocation location);

    /**
     * Teleports the entity and completes the future once the entity has
     * actually arrived (or the teleport failed/was cancelled).
     *
     * <p>Platforms with asynchronous teleports (Paper, Folia) should override
     * this so nothing that depends on the new location (effects, commands) is
     * run too early. By default the synchronous {@link #teleport} is used.
     *
     * @return a future that completes with whether the teleport succeeded.
     *     Never completes exceptionally.
     */
    default CompletableFuture<Boolean> teleportAsync(
        PlayerLocation location) {
        return CompletableFuture.completedFuture(this.teleport(location));
    }

    WorldContainer getWorld();

    String getName();

    String getWorldName();

    void setVelocity(Vector vector);
}
