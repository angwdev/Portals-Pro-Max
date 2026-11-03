package com.sekwah.advancedportals.core.util;

import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.serializeddata.Vector;

public class PlayerUtils {
    /**
     * Throws the player back opposite to the direction they are looking.
     *
     * @see #throwPlayerBack(PlayerContainer, PlayerLocation, PlayerLocation,
     *     double)
     */
    public static void throwPlayerBack(PlayerContainer player,
                                       double strength) {
        throwPlayerBack(player, null, null, strength);
    }

    /**
     * Throws the player back the way they came.
     *
     * <p>If the movement is known (from and to) the player is pushed opposite
     * to the way they were actually travelling. That is correct for walking
     * backwards, being knocked into a portal, riding, flying etc. If it isn't
     * known (or the player barely moved horizontally) it falls back to
     * opposite of the direction they are looking.
     *
     * @param from the position before the move, can be null
     * @param to the position being moved to, can be null
     */
    public static void throwPlayerBack(PlayerContainer player,
                                       PlayerLocation from, PlayerLocation to,
                                       double strength) {
        double dirX = 0;
        double dirZ = 0;

        if (from != null && to != null) {
            dirX = to.getPosX() - from.getPosX();
            dirZ = to.getPosZ() - from.getPosZ();
        }

        double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (length < 1.0E-4) {
            Vector look = player.getLoc().getDirection();
            dirX = look.getX();
            dirZ = look.getZ();
            length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        }
        if (length < 1.0E-4) {
            // Looking straight up/down while stationary, nothing sensible to
            // push against so just pop the player up. (Normalising a zero
            // vector gives NaN which the server rejects)
            player.setVelocity(new Vector(0, 0.5 * strength, 0));
            return;
        }

        // Opposite of the travel direction, with a little upwards lift
        player.setVelocity(new Vector(-dirX / length * strength,
                                      0.5 * strength,
                                      -dirZ / length * strength));
    }
}
