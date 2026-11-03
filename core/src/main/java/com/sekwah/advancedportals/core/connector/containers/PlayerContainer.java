package com.sekwah.advancedportals.core.connector.containers;

import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.serializeddata.Vector;
import java.awt.*;
import java.util.UUID;

/**
 * Just a temporary container for whenever advanced portals needs to get data
 * from a player
 */
public interface PlayerContainer extends EntityContainer, HasPermission {
    UUID getUUID();

    void sendMessage(String message);

    void sendActionBar(String message);

    void giveItem(String material, String itemName, String... itemDescription);

    /**
     * Send packets down any channel except the minecraft: one.
     * @param channel
     * @param bytes
     * @return
     */
    boolean sendPacket(String channel, byte[] bytes);

    void playSound(String sound, float volume, float pitch);

    void runTask(Runnable runnable);

    ServerContainer getServer();

    /**
     * Shows a form to the player.
     *
     * @return false if the platform can't show forms (nothing was shown)
     */
    default boolean showEditor(EditorForm form) {
        return false;
    }

    /**
     * Outlines a region with a glowing box that only this player can see.
     *
     * @param ticks how long it stays
     * @return false if the platform can't do it (nothing was shown)
     */
    default boolean highlightRegion(BlockLocation min, BlockLocation max,
                                    int ticks) {
        return false;
    }

    /**
     * Marks a position with a glowing box that only this player can see.
     *
     * @return false if the platform can't do it (nothing was shown)
     */
    default boolean highlightPoint(PlayerLocation location, int ticks) {
        return false;
    }

    GameMode getGameMode();

    default void drawLine(Vector start, Vector end, Color color,
                          float particleDensity) {
        Vector direction = end.subtract(start);
        double length = direction.length();
        if (length == 0) {
            return;
        }
        direction = direction.normalize();
        for (double i = 0; i <= length; i += particleDensity) {
            Vector pos = start.add(direction.multiply(i));
            this.spawnColoredDust(pos, 1, color);
        }
    }

    default void spawnColoredDust(Vector pos, int count, Color color) {
        spawnColoredDust(pos, 0, 0, 0, count, color);
    }

    void spawnColoredDust(Vector pos, double xSpread, double ySpread,
                          double zSpread, int count, Color color);
}
