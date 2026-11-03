package com.sekwah.advancedportals.core.util;

import static org.junit.jupiter.api.Assertions.*;

import com.sekwah.advancedportals.core.TestSupport.FakePlayer;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import org.junit.jupiter.api.Test;

class PlayerUtilsTest {
    @Test
    void pushesOppositeToTheTravelDirection() {
        FakePlayer player = new FakePlayer();
        PlayerUtils.throwPlayerBack(
            player.container(), new PlayerLocation("w", 0, 64, 0),
            new PlayerLocation("w", 0, 64, 2), 1.0);

        assertEquals(0, player.velocity.getX(), 1e-9);
        assertEquals(-1, player.velocity.getZ(), 1e-9);
        assertTrue(player.velocity.getY() > 0);
    }

    @Test
    void walkingBackwardsIntoAPortalIsPushedTheWayTheyCameFrom() {
        FakePlayer player = new FakePlayer();
        // Looking +z (yaw 0) but walking towards -x
        player.location = new PlayerLocation("w", 0, 64, 0, 0, 0);
        PlayerUtils.throwPlayerBack(
            player.container(), new PlayerLocation("w", 1, 64, 0),
            new PlayerLocation("w", 0.5, 64, 0), 1.0);

        assertTrue(player.velocity.getX() > 0);
        assertEquals(0, player.velocity.getZ(), 1e-9);
    }

    @Test
    void fallsBackToTheLookDirectionWhenNotMoving() {
        FakePlayer player = new FakePlayer();
        // yaw 0 looks towards +z
        player.location = new PlayerLocation("w", 0, 64, 0, 0, 0);
        PlayerUtils.throwPlayerBack(player.container(), 1.0);

        assertEquals(-1, player.velocity.getZ(), 1e-9);
    }

    @Test
    void neverProducesNaNWhenLookingStraightUp() {
        FakePlayer player = new FakePlayer();
        player.location = new PlayerLocation("w", 0, 64, 0, 0, -90);
        PlayerUtils.throwPlayerBack(player.container(), 0.7);

        assertFalse(Double.isNaN(player.velocity.getX()));
        assertFalse(Double.isNaN(player.velocity.getY()));
        assertFalse(Double.isNaN(player.velocity.getZ()));
    }
}
