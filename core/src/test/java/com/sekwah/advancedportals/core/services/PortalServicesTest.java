package com.sekwah.advancedportals.core.services;

import static org.junit.jupiter.api.Assertions.*;

import com.sekwah.advancedportals.core.TestSupport;
import com.sekwah.advancedportals.core.TestSupport.FakePlayer;
import com.sekwah.advancedportals.core.TestSupport.MemoryPortalRepository;
import com.sekwah.advancedportals.core.connector.containers.GameMode;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.portal.AdvancedPortal;
import com.sekwah.advancedportals.core.registry.TagRegistry;
import com.sekwah.advancedportals.core.registry.TagTarget;
import com.sekwah.advancedportals.core.repository.ConfigRepository;
import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import com.sekwah.advancedportals.core.serializeddata.PlayerData;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.services.PortalServices.PortalActivationResult;
import com.sekwah.advancedportals.core.warphandler.ActivationData;
import com.sekwah.advancedportals.core.warphandler.Tag;
import com.sekwah.advancedportals.core.warphandler.TriggerType;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortalServicesTest {
    /** Activates (and "warps") the player, ending the activation successfully */
    static class AcceptTag implements Tag.Activation {
        @Override
        public Tag.TagType[] getTagTypes() {
            return new Tag.TagType[] {Tag.TagType.PORTAL};
        }
        @Override
        public String getName() {
            return "accept";
        }
        @Override
        public String[] getAliases() {
            return null;
        }
        @Override
        public String description() {
            return "";
        }
        @Override
        public boolean preActivated(TagTarget t, PlayerContainer p,
                                    ActivationData d, String[] a) {
            return true;
        }
        @Override
        public void postActivated(TagTarget t, PlayerContainer p,
                                  ActivationData d, String[] a) {}
        @Override
        public boolean activated(TagTarget t, PlayerContainer p,
                                 ActivationData d, String[] a) {
            d.setWarpStatus(ActivationData.WarpedStatus.ACTIVATED);
            return true;
        }
    }

    /** Fails silently like the portalEvent tag does for walking into it */
    static class SilentDenyTag extends AcceptTag implements Tag.DenyBehavior {
        @Override
        public String getName() {
            return "silent";
        }
        @Override
        public boolean preActivated(TagTarget t, PlayerContainer p,
                                    ActivationData d, String[] a) {
            return false;
        }
        @Override
        public Behaviour getDenyBehavior() {
            return Behaviour.SILENT;
        }
    }

    ConfigRepository config;
    PlayerDataServices playerData;
    TagRegistry registry;
    MemoryPortalRepository repo;
    PortalServices services;
    FakePlayer player;

    @BeforeEach
    void setUp() {
        config = TestSupport.config(Map.of("getThrowbackStrength", 0.7,
                                           "getProtectionRadius", 2));
        playerData = TestSupport.playerDataServices(config);
        registry = TestSupport.tagRegistry();
        TestSupport.registerActivation(registry, new AcceptTag());
        TestSupport.registerActivation(registry, new SilentDenyTag());
        repo = new MemoryPortalRepository();
        services = TestSupport.portalServices(repo, config, playerData, registry);
        player = new FakePlayer();
    }

    private AdvancedPortal addPortal(String name, int x1, int y1, int z1,
                                     int x2, int y2, int z2, String... tags) {
        AdvancedPortal portal = TestSupport.portal(
            name, "world", x1, y1, z1, x2, y2, z2, registry, playerData, config,
            "NETHER_PORTAL");
        for (String tag : tags) {
            portal.setArgValues(tag, new String[] {"true"});
        }
        repo.stored.put(name, portal);
        services.loadPortals();
        return portal;
    }

    private PortalActivationResult move(double x, double y, double z) {
        return services.checkPortalActivation(
            player.container(), new PlayerLocation("world", x, y, z, 0, 0),
            TriggerType.MOVEMENT);
    }

    private void blockAt(int x, int y, int z, String material) {
        player.blocks.put(new BlockLocation("world", x, y, z), material);
    }

    @Test
    void walkingIntoATriggerBlockActivatesOnce() {
        addPortal("a", 0, 64, 0, 1, 65, 0, "accept");
        blockAt(0, 64, 0, "NETHER_PORTAL");

        assertEquals(PortalActivationResult.PORTAL_ACTIVATED, move(0.5, 64, 0.5));
        // Standing in it must not activate again
        assertEquals(PortalActivationResult.PORTAL_DENIED, move(0.6, 64, 0.5));
        // Leaving clears the state so it can be used again
        assertEquals(PortalActivationResult.NOT_IN_PORTAL, move(5.5, 64, 5.5));
        assertEquals(PortalActivationResult.PORTAL_ACTIVATED, move(0.5, 64, 0.5));
    }

    @Test
    void regionWithoutTheTriggerBlockDoesNothing() {
        addPortal("a", 0, 64, 0, 1, 65, 0, "accept");
        // The blocks are air
        assertEquals(PortalActivationResult.NOT_IN_PORTAL, move(0.5, 64, 0.5));
    }

    @Test
    void headIsCheckedAtTheRealHeadBlockNotFeetPlusOne() {
        // 1 block high portal at y=66 only
        addPortal("a", 0, 66, 0, 0, 66, 0, "accept");
        blockAt(0, 66, 0, "NETHER_PORTAL");

        // Feet in block 64, hitbox reaches y=66.3 so the head is in block 66.
        // The old calculation looked at 64 + floor(1.8) = 65 and missed it.
        assertEquals(PortalActivationResult.PORTAL_ACTIVATED, move(0.5, 64.5, 0.5));
    }

    @Test
    void blockTheHitboxJustTouchesFromBelowDoesNotCount() {
        addPortal("a", 0, 66, 0, 0, 66, 0, "accept");
        blockAt(0, 66, 0, "NETHER_PORTAL");

        // Hitbox spans 64.2 -> 66.0 which only touches the bottom of block 66
        assertEquals(PortalActivationResult.NOT_IN_PORTAL, move(0.5, 64.2, 0.5));
        // 64.0 -> 65.8 doesn't reach it at all
        assertEquals(PortalActivationResult.NOT_IN_PORTAL, move(0.5, 64.0, 0.5));
    }

    @Test
    void middleBlockOfTheHitboxCounts() {
        // Feet in 64, head in 66, only the middle block 65 is the portal
        addPortal("a", 0, 65, 0, 0, 65, 0, "accept");
        blockAt(0, 65, 0, "NETHER_PORTAL");

        assertEquals(PortalActivationResult.PORTAL_ACTIVATED, move(0.5, 64.5, 0.5));
    }

    @Test
    void silentFailureDoesNotMarkThePlayerAsInThePortal() {
        // A "delayed" portal: walking in must leave the player free to be
        // activated by the portal event afterwards.
        addPortal("a", 0, 64, 0, 0, 64, 0, "silent");
        blockAt(0, 64, 0, "NETHER_PORTAL");

        assertEquals(PortalActivationResult.NOT_IN_PORTAL, move(0.5, 64, 0.5));
        PlayerData data = playerData.getPlayerData(player.container());
        assertNull(data.inPortal());
    }

    @Test
    void deniedPlayersAreThrownBackAwayFromTheirMovement() {
        // No tags: nothing activates so the player is knocked back
        addPortal("a", 0, 64, 0, 0, 64, 0);
        blockAt(0, 64, 0, "NETHER_PORTAL");

        PlayerLocation from = new PlayerLocation("world", -1.5, 64, 0.5, 0, 0);
        PlayerLocation to = new PlayerLocation("world", 0.5, 64, 0.5, 0, 0);
        PortalActivationResult result = services.checkPortalActivation(
            player.container(), from, to, TriggerType.MOVEMENT);

        assertEquals(PortalActivationResult.PORTAL_DENIED, result);
        assertNotNull(player.velocity);
        // Moved towards +x so the push has to be towards -x
        assertTrue(player.velocity.getX() < 0);
        assertEquals(0, player.velocity.getZ(), 1e-9);
    }

    @Test
    void spectatorsAreIgnoredWhenConfigured() {
        config = TestSupport.config(Map.of("blockSpectatorMode", true));
        services = TestSupport.portalServices(repo, config, playerData, registry);
        addPortal("a", 0, 64, 0, 0, 64, 0, "accept");
        blockAt(0, 64, 0, "NETHER_PORTAL");
        player.gameMode = GameMode.SPECTATOR;

        assertEquals(PortalActivationResult.NOT_IN_PORTAL, move(0.5, 64, 0.5));
    }

    @Test
    void protectionUsesTheConfiguredRadius() {
        addPortal("a", 10, 64, 10, 10, 64, 10);
        assertTrue(services.inPortalRegionProtected(new BlockLocation("world", 12, 64, 10)));
        assertFalse(services.inPortalRegionProtected(new BlockLocation("world", 13, 64, 10)));
        assertTrue(services.inPortalRegionProtected("world", 8, 62, 8));
        assertFalse(services.inPortalRegionProtected("nether", 10, 64, 10));
    }

    @Test
    void renameKeepsTheRegionAndUpdatesTheIndexAndFile() {
        AdvancedPortal portal = addPortal("old", 0, 64, 0, 1, 65, 1, "accept");

        assertTrue(services.renamePortal("old", "new", null));

        assertNull(services.getPortal("old"));
        assertSame(portal, services.getPortal("new"));
        assertEquals("new", portal.getName());
        assertEquals("new", services.inPortalRegionGetName(new BlockLocation("world", 0, 64, 0)));
        assertTrue(repo.stored.containsKey("new"));
        assertFalse(repo.stored.containsKey("old"));
    }

    @Test
    void renameRefusesTakenAndInvalidNames() {
        addPortal("one", 0, 64, 0, 1, 65, 1);
        addPortal("two", 10, 64, 10, 11, 65, 11);

        assertFalse(services.renamePortal("one", "two", null));
        assertFalse(services.renamePortal("one", "has space", null));
        assertFalse(services.renamePortal("missing", "x", null));
        assertNotNull(services.getPortal("one"));
    }

    @Test
    void movingAPortalSavesAndMovesTheIndexEntry() {
        AdvancedPortal portal = addPortal("a", 0, 64, 0, 1, 65, 1);
        assertEquals("a", services.inPortalRegionGetName(new BlockLocation("world", 0, 64, 0)));

        assertTrue(services.updatePortalRegion(
            portal, new BlockLocation("world", 100, 70, 100),
            new BlockLocation("world", 98, 68, 99)));

        assertNull(services.inPortalRegionGetName(new BlockLocation("world", 0, 64, 0)));
        assertEquals("a", services.inPortalRegionGetName(new BlockLocation("world", 99, 69, 100)));
        // Corners are sorted
        assertEquals(98, portal.getMinLoc().getPosX());
        assertEquals(100, portal.getMaxLoc().getPosX());
    }

    @Test
    void removeDeletesFromCacheIndexAndRepository() {
        addPortal("a", 0, 64, 0, 1, 65, 1);

        assertTrue(services.removePortal("a", null));

        assertNull(services.getPortal("a"));
        assertNull(services.inPortalRegionGetName(new BlockLocation("world", 0, 64, 0)));
        assertTrue(repo.stored.isEmpty());
        assertFalse(services.removePortal("a", null));
    }

    @Test
    void lookupsIgnoreCase() {
        AdvancedPortal portal = addPortal("MyPortal", 0, 64, 0, 1, 65, 1);
        assertSame(portal, services.getPortal("myportal"));
        assertEquals("MyPortal", services.getPortalKey("MYPORTAL"));
    }
}
