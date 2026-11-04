package com.sekwah.advancedportals.core.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.sekwah.advancedportals.core.AdvancedPortalsCore;
import com.sekwah.advancedportals.core.TestSupport;
import com.sekwah.advancedportals.core.TestSupport.FakePlayer;
import com.sekwah.advancedportals.core.TestSupport.FakeSender;
import com.sekwah.advancedportals.core.commands.CommandTemplate;
import com.sekwah.advancedportals.core.connector.commands.CommandRegister;
import com.sekwah.advancedportals.core.connector.containers.ServerContainer;
import com.sekwah.advancedportals.core.connector.containers.WorldContainer;
import com.sekwah.advancedportals.core.destination.Destination;
import com.sekwah.advancedportals.core.permissions.Permissions;
import com.sekwah.advancedportals.core.portal.AdvancedPortal;
import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import com.sekwah.advancedportals.core.serializeddata.PlayerData;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.services.DestinationServices;
import com.sekwah.advancedportals.core.services.PortalServices;
import com.sekwah.advancedportals.core.util.InfoLogger;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Boots the real core (dependency injection, YAML storage, translations,
 * commands) with a fake server and runs the portal/destination commands.
 */
class CommandsIntegrationTest {
    @TempDir
    Path dataDir;

    AdvancedPortalsCore core;
    PortalServices portals;
    DestinationServices destinations;
    final Map<String, CommandTemplate> commands = new HashMap<>();
    FakePlayer player;
    FakeSender sender;
    final List<String> warnings = new ArrayList<>();

    @BeforeEach
    void boot() {
        Permissions.hasPermissionManager = false;
        List<String> triggerBlocks =
            Arrays.asList("NETHER_PORTAL", "END_PORTAL", "WATER", "END_GATEWAY");
        ServerContainer server = TestSupport.proxy(
            ServerContainer.class,
            Map.of("getAllTriggerBlocks", a -> triggerBlocks,
                   "getCommonTriggerBlocks", a -> triggerBlocks,
                   "matchMaterialName",
                   a -> triggerBlocks.contains(((String) a[0]).toUpperCase())
                       ? ((String) a[0]).toUpperCase()
                       : null,
                   "getWorld", a -> TestSupport.proxy(WorldContainer.class, Map.of())));
        InfoLogger logger = new InfoLogger() {
            @Override
            public void warning(String s) {
                warnings.add(s);
            }
            @Override
            public void info(String s) {}
            @Override
            public void error(Exception e) {
                e.printStackTrace();
            }
        };
        core = new AdvancedPortalsCore("1.21.0", dataDir.toFile(), logger, server);
        CommandRegister register = commands::put;
        core.getModule().addInstanceBinding(CommandRegister.class, register);
        core.getModule().getInjector().injectMembers(core);
        core.onEnable();
        portals = core.getPortalServices();
        destinations = core.getDestinationServices();

        player = new FakePlayer();
        player.location = new PlayerLocation("world", 10.25, 64.0, -3.75, 90f, 15f);
        sender = new FakeSender(player);

        PlayerData data = core.getPlayerDataRepository().getPlayerData(player.container());
        data.setPos1(new BlockLocation("world", 0, 64, 0));
        data.setPos2(new BlockLocation("world", 1, 65, 0));
    }

    @AfterEach
    void cleanUp() {
        Permissions.hasPermissionManager = false;
    }

    private void portal(String... args) {
        commands.get("portal").onCommand(sender.container(), "portal", args);
    }

    private void desti(String... args) {
        commands.get("destination").onCommand(sender.container(), "desti", args);
    }

    private void reloadFromDisk() {
        portals.loadPortals();
        destinations.loadDestinations();
    }

    private static String join(String[] values) {
        return values == null ? null : String.join("|", values);
    }

    @Test
    void filesFromAnAdvancedPortalsInstallStillLoad() throws Exception {
        java.nio.file.Files.createDirectories(dataDir.resolve("portals"));
        java.nio.file.Files.createDirectories(dataDir.resolve("desti"));
        java.nio.file.Files.createDirectories(dataDir.resolve("lang"));
        // Files as written by Advanced Portals 2.x: no triggerWithHitbox in the
        // config, a customised translation with the old prefix
        java.nio.file.Files.writeString(dataDir.resolve("config.yaml"),
            "portalProtectionRadius: 9\nthrowbackStrength: 1.5\n"
            + "translationFile: en_GB\nwarpEffect:\n  enabled: false\n"
            + "  soundEffect: ender\n  visualEffect: ender\n"
            + "commandPortals:\n  console: false\n  enabled: true\n  op: true\n"
            + "  permsWildcard: true\n  proxy: true\n");
        java.nio.file.Files.writeString(dataDir.resolve("lang/en_GB.lang"),
            "messageprefix.positive=&a[&eAdvancedPortals&a]\n"
            + "command.create.complete=Old custom text\n");
        java.nio.file.Files.writeString(dataDir.resolve("portals/old.yaml"),
            "args:\n  name:\n  - old\n  destination:\n  - spawn\n"
            + "  triggerblock:\n  - NETHER_PORTAL\n  cooldown:\n  - '3'\n"
            + "maxLoc:\n  posX: 5\n  posY: 70\n  posZ: 5\n  worldName: world\n"
            + "minLoc:\n  posX: 3\n  posY: 68\n  posZ: 3\n  worldName: world\n");
        java.nio.file.Files.writeString(dataDir.resolve("desti/spawn.yaml"),
            "args:\n  name:\n  - spawn\n  message:\n  - Welcome\n"
            + "loc:\n  pitch: 12.5\n  worldName: world\n  x: 1.5\n  y: 64.0\n"
            + "  yaw: 180.0\n  z: -2.25\n");

        core.loadPortalConfig();
        portals.loadPortals();
        destinations.loadDestinations();

        AdvancedPortal old = portals.getPortal("old");
        assertNotNull(old);
        assertEquals("spawn", join(old.getArgValues("destination")));
        assertEquals("3", join(old.getArgValues("cooldown")));
        assertEquals(3, old.getMinLoc().getPosX());
        assertEquals("old", portals.inPortalRegionGetName(new BlockLocation("world", 4, 69, 4)));
        Destination spawn = destinations.getDestination("spawn");
        assertEquals(1.5, spawn.getLoc().getPosX(), 0);
        assertEquals(180f, spawn.getLoc().getYaw(), 0);
        assertEquals("Welcome", join(spawn.getArgValues("message")));

        // Values that were in the old config are kept, new options get their
        // default and are written to the file for next time
        assertEquals(9, core.getModule().getInjector().getInstance(
            com.sekwah.advancedportals.core.repository.ConfigRepository.class).getProtectionRadius());
        assertFalse(core.getModule().getInjector().getInstance(
            com.sekwah.advancedportals.core.repository.ConfigRepository.class).getTriggerWithHitbox());
        assertTrue(java.nio.file.Files.readString(dataDir.resolve("config.yaml")).contains("triggerWithHitbox"));

        // Customised translations still win, keys added since come from the jar
        com.sekwah.advancedportals.core.util.Lang.loadLanguage("en_GB");
        assertTrue(com.sekwah.advancedportals.core.util.Lang.translate("command.create.complete").contains("Old custom text"));
        assertTrue(com.sekwah.advancedportals.core.util.Lang.translate("command.edit.nochange").contains("Nothing was changed"));
    }

    @Test
    void translationsAreLoaded() {
        portal("create", "p1", "triggerblock:NETHER_PORTAL");
        assertTrue(sender.all().contains("successfully created"), sender.all());
    }

    @Test
    void createdPortalsAreSavedAndReloaded() {
        portal("create", "p1", "triggerblock:NETHER_PORTAL");

        assertNotNull(portals.getPortal("p1"));
        assertTrue(new File(dataDir.toFile(), "portals/p1.yaml").exists());

        reloadFromDisk();
        AdvancedPortal loaded = portals.getPortal("p1");
        assertNotNull(loaded);
        assertEquals("p1", loaded.getName());
        assertEquals("NETHER_PORTAL", join(loaded.getArgValues("triggerblock")));
        assertEquals(1, loaded.getMaxLoc().getPosX());
        assertEquals("p1", portals.inPortalRegionGetName(new BlockLocation("world", 1, 65, 0)));
    }

    @Test
    void editSetsAddsAndRemovesAndPersists() {
        desti("create", "spawn");
        desti("create", "hub");
        portal("create", "p1", "triggerblock:NETHER_PORTAL", "destination:spawn");

        portal("edit", "p1", "add", "destination:hub");
        assertEquals("spawn|hub", join(portals.getPortal("p1").getArgValues("destination")));

        portal("edit", "p1", "remove", "destination:spawn");
        assertEquals("hub", join(portals.getPortal("p1").getArgValues("destination")));

        // Plain set replaces, aliases work, quoted values keep their commas
        portal("edit", "p1", "desti:spawn", "message:\"Welcome, traveler\"", "cooldown:5");
        AdvancedPortal portal = portals.getPortal("p1");
        assertEquals("spawn", join(portal.getArgValues("destination")));
        assertEquals("Welcome, traveler", join(portal.getArgValues("message")));
        assertEquals("5", join(portal.getArgValues("cooldown")));

        portal("edit", "p1", "remove", "message", "cooldown");
        assertNull(portals.getPortal("p1").getArgValues("message"));

        // Everything above has to survive a restart
        reloadFromDisk();
        portal = portals.getPortal("p1");
        assertEquals("spawn", join(portal.getArgValues("destination")));
        assertNull(portal.getArgValues("message"));
        assertEquals("NETHER_PORTAL", join(portal.getArgValues("triggerblock")));
    }

    @Test
    void invalidEditsAreRejectedAndChangeNothing() {
        portal("create", "p1", "triggerblock:NETHER_PORTAL");

        sender.messages.clear();
        portal("edit", "p1", "cooldown:abc");
        assertNull(portals.getPortal("p1").getArgValues("cooldown"));

        portal("edit", "p1", "triggerblock:NOT_A_BLOCK");
        assertEquals("NETHER_PORTAL", join(portals.getPortal("p1").getArgValues("triggerblock")));

        portal("edit", "p1", "name:other");
        assertEquals("p1", portals.getPortal("p1").getName());

        sender.messages.clear();
        portal("edit", "p1", "bogus:1");
        assertTrue(sender.all().contains("bogus"), sender.all());

        sender.messages.clear();
        portal("edit", "missing", "cooldown:5");
        assertTrue(sender.all().contains("missing"), sender.all());
    }

    @Test
    void renamingAPortalMovesTheFileAndKeepsTheTags() {
        portal("create", "p1", "triggerblock:NETHER_PORTAL", "message:hi");

        portal("rename", "p1", "p2");

        assertNull(portals.getPortal("p1"));
        assertNotNull(portals.getPortal("p2"));
        assertFalse(new File(dataDir.toFile(), "portals/p1.yaml").exists());
        assertTrue(new File(dataDir.toFile(), "portals/p2.yaml").exists());
        reloadFromDisk();
        assertEquals("hi", join(portals.getPortal("p2").getArgValues("message")));
        assertEquals("p2", portals.getPortal("p2").getName());
    }

    @Test
    void movingAPortalIsSavedToDisk() {
        portal("create", "p1", "triggerblock:NETHER_PORTAL");
        PlayerData data = core.getPlayerDataRepository().getPlayerData(player.container());
        data.setPos1(new BlockLocation("world", 50, 70, 50));
        data.setPos2(new BlockLocation("world", 52, 72, 51));

        portal("move", "p1");

        // This used to be lost on the next reload
        reloadFromDisk();
        AdvancedPortal portal = portals.getPortal("p1");
        assertEquals(50, portal.getMinLoc().getPosX());
        assertEquals(52, portal.getMaxLoc().getPosX());
        assertNull(portals.inPortalRegionGetName(new BlockLocation("world", 0, 64, 0)));
        assertEquals("p1", portals.inPortalRegionGetName(new BlockLocation("world", 51, 71, 50)));
    }

    @Test
    void regionCommandLoadsTheSelection() {
        portal("create", "p1", "triggerblock:NETHER_PORTAL");
        PlayerData data = core.getPlayerDataRepository().getPlayerData(player.container());
        data.setPos1(new BlockLocation("world", 9, 9, 9));
        data.setPos2(new BlockLocation("world", 9, 9, 9));

        portal("region", "p1");

        assertEquals(0, data.getPos1().getPosX());
        assertEquals(1, data.getPos2().getPosX());
    }

    @Test
    void destinationsKeepTheExactLocationAndFacing() {
        desti("create", "spawn");
        reloadFromDisk();

        Destination destination = destinations.getDestination("spawn");
        assertEquals(10.25, destination.getLoc().getPosX(), 0);
        assertEquals(-3.75, destination.getLoc().getPosZ(), 0);
        assertEquals(90f, destination.getLoc().getYaw(), 0);
        assertEquals(15f, destination.getLoc().getPitch(), 0);
    }

    @Test
    void movingADestinationUsesThePlayersExactPosition() {
        desti("create", "spawn");
        player.location = new PlayerLocation("world", -100.5, 80.125, 7.875, -45f, -10f);

        desti("move", "spawn");

        reloadFromDisk();
        PlayerLocation loc = destinations.getDestination("spawn").getLoc();
        assertEquals(-100.5, loc.getPosX(), 0);
        assertEquals(80.125, loc.getPosY(), 0);
        assertEquals(-45f, loc.getYaw(), 0);
        assertEquals(-10f, loc.getPitch(), 0);
    }

    @Test
    void renamingADestinationUpdatesThePortalsUsingIt() {
        desti("create", "spawn");
        portal("create", "p1", "triggerblock:NETHER_PORTAL", "destination:spawn");

        desti("rename", "spawn", "home");

        assertNull(destinations.getDestination("spawn"));
        assertEquals("home", destinations.getDestination("home").getName());
        assertEquals("home", join(portals.getPortal("p1").getArgValues("destination")));
        reloadFromDisk();
        assertEquals("home", join(portals.getPortal("p1").getArgValues("destination")));
        assertEquals("home", destinations.getDestination("home").getName());
    }

    @Test
    void destinationEditAndInfo() {
        desti("create", "spawn");
        portal("create", "p1", "triggerblock:NETHER_PORTAL", "destination:spawn");

        desti("edit", "spawn", "message:\"Welcome home, @player\"", "permission:my.perm");
        reloadFromDisk();
        Destination destination = destinations.getDestination("spawn");
        assertEquals("Welcome home, @player", join(destination.getArgValues("message")));
        assertEquals("my.perm", join(destination.getArgValues("permission")));

        // Tags that only make sense on portals can't be put on destinations
        sender.messages.clear();
        desti("edit", "spawn", "triggerblock:NETHER_PORTAL");
        assertNull(destinations.getDestination("spawn").getArgValues("triggerblock"));

        sender.messages.clear();
        desti("info", "spawn");
        String info = sender.all();
        assertTrue(info.contains("10.250"), info);
        assertTrue(info.contains("p1"), info);
    }

    @Test
    void removedDestinationsWarnAboutPortalsStillUsingThem() {
        desti("create", "spawn");
        portal("create", "p1", "triggerblock:NETHER_PORTAL", "destination:spawn");

        sender.messages.clear();
        desti("remove", "spawn");

        assertNull(destinations.getDestination("spawn"));
        assertTrue(sender.all().contains("p1"), sender.all());
    }

    @Test
    void commandTagCannotSmuggleInCommandsThePlayerIsNotAllowedToCreate() {
        // The first command is harmless, the second would run as op
        Permissions.hasPermissionManager = true;
        player.permissions = perm -> !perm.contains("command_level");
        com.sekwah.advancedportals.core.serializeddata.config.Config config =
            TestSupport.get(
                core.getModule().getInjector().getInstance(
                    com.sekwah.advancedportals.core.repository.ConfigRepository.class),
                "config");
        config.commandPortals.enabled = true;
        config.commandPortals.op = true;
        portal("create", "p1", "triggerblock:NETHER_PORTAL");

        portal("edit", "p1", "command:say hi,!op @player");

        assertNull(portals.getPortal("p1").getArgValues("command"));
    }

    @Test
    void commandTagAllowsEveryCommandWhenThePlayerHasThePermissions() {
        Permissions.hasPermissionManager = true;
        player.permissions = perm -> true;
        portal("create", "p1", "triggerblock:NETHER_PORTAL");

        portal("edit", "p1", "command:say hi,!op @player");

        assertEquals("say hi|!op @player",
                     join(portals.getPortal("p1").getArgValues("command")));
    }

    @Test
    void editWithoutTagsOpensTheEditorAndAppliesOnlyChangedFields() {
        player.supportsEditor = true;
        desti("create", "spawn");
        portal("create", "p1", "triggerblock:NETHER_PORTAL", "destination:spawn",
               "cooldown:5");

        portal("edit", "p1");

        assertNotNull(player.form);
        assertTrue(player.form.title.contains("p1"));
        var names = new ArrayList<String>();
        for (var field : player.form.fields) names.add(field.name);
        assertFalse(names.contains("name"));
        assertTrue(names.contains("destination") && names.contains("cooldown"));

        Map<String, String> values = new HashMap<>();
        for (var field : player.form.fields) values.put(field.name, field.initial);
        values.put("cooldown", "");
        values.put("message", "Hello, there");
        values.put("destination", "spawn");
        player.form.onSubmit.accept(player.container(), values);

        AdvancedPortal portal = portals.getPortal("p1");
        assertNull(portal.getArgValues("cooldown"));
        assertEquals("Hello, there", join(portal.getArgValues("message")));
        assertEquals("spawn", join(portal.getArgValues("destination")));
        reloadFromDisk();
        assertEquals("Hello, there", join(portals.getPortal("p1").getArgValues("message")));
    }

    @Test
    void editorSubmitWithNoChangesDoesNothing() {
        player.supportsEditor = true;
        portal("create", "p1", "triggerblock:NETHER_PORTAL");
        portal("edit", "p1");
        Map<String, String> values = new HashMap<>();
        for (var field : player.form.fields) values.put(field.name, field.initial);

        sender.messages.clear();
        player.form.onSubmit.accept(player.container(), values);

        assertTrue(sender.all().contains("Nothing was changed"), sender.all());
    }

    @Test
    void editWithoutTagsExplainsWhenTheEditorIsNotAvailable() {
        portal("create", "p1", "triggerblock:NETHER_PORTAL");
        sender.messages.clear();

        portal("edit", "p1");

        assertTrue(sender.all().contains("Paper"), sender.all());
    }

    @Test
    void highlightAsksThePlatformToOutlineThePortal() {
        player.supportsHighlight = true;
        portal("create", "p1", "triggerblock:NETHER_PORTAL");
        player.highlighted.clear();

        portal("highlight", "p1", "30");

        assertEquals(1, player.highlighted.size());
        assertEquals(1, player.highlighted.get(0)[1].getPosX());
        assertTrue(sender.all().contains("30"), sender.all());
    }

    @Test
    void keepVelocityOnlyAcceptsBooleans() {
        portal("create", "p1", "triggerblock:NETHER_PORTAL", "keepvelocity:true");
        assertEquals("true", join(portals.getPortal("p1").getArgValues("keepvelocity")));

        portal("edit", "p1", "momentum:maybe");
        assertEquals("true", join(portals.getPortal("p1").getArgValues("keepvelocity")));

        portal("edit", "p1", "momentum:false");
        assertEquals("false", join(portals.getPortal("p1").getArgValues("keepvelocity")));
    }

    @Test
    void positionCommandsSelectWithoutTheAxe() {
        player.location = new PlayerLocation("world", 10.5, 64, -3.5, 0, 0);
        PlayerData data = core.getPlayerDataRepository().getPlayerData(player.container());

        portal("pos1");
        assertEquals(10, data.getPos1().getPosX());
        assertEquals(-4, data.getPos1().getPosZ());

        portal("pos2", "20", "~1", "~-2");
        assertEquals(20, data.getPos2().getPosX());
        assertEquals(65, data.getPos2().getPosY());
        assertEquals(-6, data.getPos2().getPosZ());

        sender.messages.clear();
        portal("pos2", "x", "y", "z");
        assertTrue(sender.all().contains("Usage"), sender.all());

        // and the selection can be turned into a portal
        portal("create", "p1", "triggerblock:NETHER_PORTAL");
        assertEquals(10, portals.getPortal("p1").getMinLoc().getPosX());
        assertEquals(20, portals.getPortal("p1").getMaxLoc().getPosX());
    }

    @Test
    void consoleCanEditToo() {
        portal("create", "p1", "triggerblock:NETHER_PORTAL");
        FakeSender console = new FakeSender();

        commands.get("portal").onCommand(console.container(), "portal",
                                         new String[] {"edit", "p1", "cooldown:3"});

        assertEquals("3", join(portals.getPortal("p1").getArgValues("cooldown")));
        assertTrue(console.all().contains("updated"), console.all());
    }

    @Test
    void tabCompletionOffersNamesTagsAndModes() {
        desti("create", "spawn");
        portal("create", "p1", "triggerblock:NETHER_PORTAL");

        List<String> names = commands.get("portal").onTabComplete(
            sender.container(), new String[] {"edit", ""});
        assertTrue(names.contains("p1"));

        List<String> modes = commands.get("portal").onTabComplete(
            sender.container(), new String[] {"edit", "p1", ""});
        assertTrue(modes.contains("add") && modes.contains("remove"));
        assertTrue(modes.contains("destination:"));

        List<String> removable = commands.get("portal").onTabComplete(
            sender.container(), new String[] {"edit", "p1", "remove", ""});
        assertTrue(removable.contains("triggerblock"));
        assertFalse(removable.contains("name"));
    }
}
