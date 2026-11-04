package com.sekwah.advancedportals.paper;

import com.sekwah.advancedportals.core.AdvancedPortalsCore;
import com.sekwah.advancedportals.core.connector.commands.CommandRegister;
import com.sekwah.advancedportals.core.module.AdvancedPortalsModule;
import com.sekwah.advancedportals.core.permissions.Permissions;
import com.sekwah.advancedportals.core.repository.ConfigRepository;
import com.sekwah.advancedportals.core.services.DestinationServices;
import com.sekwah.advancedportals.core.services.PortalServices;
import com.sekwah.advancedportals.core.util.GameScheduler;
import com.sekwah.advancedportals.shadowed.inject.Inject;
import com.sekwah.advancedportals.shadowed.inject.Injector;
import com.sekwah.advancedportals.paper.commands.subcommands.portal.ImportPortalSubCommand;
import com.sekwah.advancedportals.paper.connector.command.PaperCommandRegister;
import com.sekwah.advancedportals.paper.connector.container.PaperServerContainer;
import com.sekwah.advancedportals.paper.importer.LegacyImporter;
import com.sekwah.advancedportals.paper.tags.ConditionsTag;
import com.sekwah.advancedportals.paper.tags.CostTag;
import com.sekwah.advancedportals.paper.warpeffects.PaperWarpEffects;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.event.HandlerList;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class PortalsProMaxPlugin extends JavaPlugin {
    private AdvancedPortalsCore portalsCore;
    private PhysicsListeners physicsListeners;
    private boolean physicsEventsRegistered;

    @Inject
    DestinationServices destinationServices;

    @Inject
    PortalServices portalServices;

    @Inject
    ConfigRepository configRepo;

    private static PortalsProMaxPlugin instance;

    public static PortalsProMaxPlugin getInstance() {
        return instance;
    }

    public PortalsProMaxPlugin() {
        instance = this;
    }

    @Override
    public void onEnable() {
        this.migrateOldDataFolder();

        Permissions.hasPermissionManager = true;

        String mcVersion = this.getServer().getVersion();
        Pattern pattern = Pattern.compile("\\(MC: ([\\d.]+)\\)");
        Matcher matcher = pattern.matcher(mcVersion);
        PaperServerContainer serverContainer =
            new PaperServerContainer(this.getServer());
        this.portalsCore = new AdvancedPortalsCore(
            matcher.find() ? matcher.group(1) : "0.0.0", this.getDataFolder(),
            new PaperInfoLogger(this), serverContainer);
        AdvancedPortalsModule module = this.portalsCore.getModule();

        module.addInstanceBinding(CommandRegister.class,
                                  new PaperCommandRegister(this));

        Injector injector = module.getInjector();

        injector.injectMembers(this);
        injector.injectMembers(this.portalsCore);
        injector.injectMembers(serverContainer);

        Listeners listeners = injector.getInstance(Listeners.class);
        injector.injectMembers(listeners);
        this.getServer().getPluginManager().registerEvents(listeners, this);

        this.physicsListeners = injector.getInstance(PhysicsListeners.class);
        injector.injectMembers(this.physicsListeners);
        this.portalsCore.registerConfigReloadListener(
                this::syncPhysicsEventRegistry);

        GameScheduler scheduler = injector.getInstance(GameScheduler.class);
        this.getServer().getGlobalRegionScheduler().runAtFixedRate(
            this, (_) -> scheduler.tick(), 1, 1);

        PaperWarpEffects warpEffects = new PaperWarpEffects();
        injector.injectMembers(warpEffects);
        warpEffects.registerEffects();

        // Try to do this after setting up everything that would need to be
        // injected to.
        this.portalsCore.onEnable();

        this.portalsCore.registerPortalCommand("import",
                                               new ImportPortalSubCommand());

        checkAndCreateConfig();
        registerPlaceholderAPI();
        registerVault();
    }

    /**
     * The plugin used to be called AdvancedPortals. If there is data from it
     * and none for this plugin yet, copy it over (the old folder is left
     * untouched so nothing can be lost).
     */
    private void migrateOldDataFolder() {
        File newFolder = this.getDataFolder();
        File oldFolder = new File(newFolder.getParentFile(), "AdvancedPortals");
        if (newFolder.exists() || !oldFolder.isDirectory()) {
            return;
        }
        try {
            Path source = oldFolder.toPath();
            Path target = newFolder.toPath();
            try (Stream<Path> paths = Files.walk(source)) {
                for (Path path : (Iterable<Path>) paths::iterator) {
                    Path destination = target.resolve(source.relativize(path));
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.copy(path, destination);
                    }
                }
            }
            getLogger().info("Copied the data of the old AdvancedPortals plugin "
                             + "to " + newFolder.getName());
        } catch (IOException e) {
            getLogger().log(Level.WARNING,
                            "Could not copy the old AdvancedPortals data", e);
        }
    }

    private void registerVault() {
        if (this.getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> economyProvider =
            this.getServer().getServicesManager().getRegistration(
                Economy.class);
        if (economyProvider == null) {
            return;
        }
        AdvancedPortalsCore.getInstance().getTagRegistry().registerTag(
            new CostTag(economyProvider.getProvider()));
    }

    private void checkAndCreateConfig() {
        if (!this.getDataFolder().exists()) {
            this.getDataFolder().mkdirs();
        }

        File destiFile = new File(this.getDataFolder(), "destinations.yml");
        File destiFolder = new File(this.getDataFolder(), "desti");
        if (destiFile.exists() && !destiFolder.exists()) {
            destiFolder.mkdirs();
            getLogger().info(
                "Importing old destinations from destinations.yml");
            LegacyImporter.importDestinations(this.destinationServices);
        }

        File portalFile = new File(this.getDataFolder(), "portals.yml");
        File portalFolder = new File(this.getDataFolder(), "portals");
        if (portalFile.exists() && !portalFolder.exists()) {
            portalFolder.mkdirs();
            getLogger().info("Importing old portals from portals.yml");
            LegacyImporter.importPortals(this.portalServices);
        }

        // Check if config.yml exists and config.yaml doesnt exist
        File configFile = new File(this.getDataFolder(), "config.yml");
        File configYamlFile = new File(this.getDataFolder(), "config.yaml");
        if (configFile.exists() && !configYamlFile.exists()) {
            LegacyImporter.importConfig(this.configRepo);
        }
    }

    private void syncPhysicsEventRegistry() {
        boolean shouldRegister = this.configRepo.getDisablePhysicsEvents();
        if (shouldRegister && !this.physicsEventsRegistered) {
            this.getServer().getPluginManager().registerEvents(
                    this.physicsListeners, this);
            this.physicsEventsRegistered = true;
        }
        else if (!shouldRegister && this.physicsEventsRegistered) {
            HandlerList.unregisterAll(this.physicsListeners);
            this.physicsEventsRegistered = false;
        }
    }

    @Override
    public void onDisable() {
        this.portalsCore.onDisable();
    }

    private void registerPlaceholderAPI() {
        if (this.getServer().getPluginManager().getPlugin("PlaceholderAPI")
            != null) {
            AdvancedPortalsCore.getInstance().getTagRegistry().registerTag(
                new ConditionsTag());
        }
    }
}
