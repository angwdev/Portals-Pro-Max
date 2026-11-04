package com.sekwah.advancedportals.core.connector.containers;

/**
 * Optional behaviour of a teleport. Platforms ignore what they don't support.
 */
public enum TeleportOption {
    /**
     * The entity keeps its momentum (direction and speed) through the
     * teleport instead of coming to a stop.
     */
    KEEP_VELOCITY
}
