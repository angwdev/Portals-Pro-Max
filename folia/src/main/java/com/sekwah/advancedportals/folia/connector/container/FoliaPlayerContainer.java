package com.sekwah.advancedportals.folia.connector.container;

import com.sekwah.advancedportals.core.AdvancedPortalsCore;
import com.sekwah.advancedportals.core.connector.containers.EditorForm;
import com.sekwah.advancedportals.core.connector.containers.GameMode;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.connector.containers.ServerContainer;
import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.serializeddata.Vector;
import com.sekwah.advancedportals.core.util.Lang;
import com.sekwah.advancedportals.shadowed.inject.Inject;
import com.sekwah.advancedportals.folia.AdvancedPortalsPlugin;
import com.sekwah.advancedportals.folia.paper.PaperDialogs;
import com.sekwah.advancedportals.folia.paper.PaperHighlights;
import com.sekwah.advancedportals.folia.paper.PaperText;
import java.awt.*;
import java.util.Arrays;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Just a temporary container for whenever advanced portals needs to get data
 * from a player
 */
public class FoliaPlayerContainer
    extends FoliaEntityContainer implements PlayerContainer {
    @Inject
    private AdvancedPortalsCore portalsCore;

    private final Player player;

    public FoliaPlayerContainer(Player player) {
        super(player);
        this.player = player;
    }

    @Override
    public UUID getUUID() {
        return player.getUniqueId();
    }

    @Override
    public void sendMessage(String message) {
        if (message.isBlank()
                || message.equals(Lang.getPositivePrefix())
                || message.equals(Lang.getNegativePrefix())) return;
        player.sendMessage(message);
    }

    /**
     * Runs on the thread that currently owns the player. The entity
     * scheduler follows the player between regions (the region scheduler at
     * the players old location did not, and reading the location from a thread
     * that doesn't own the player is not allowed on Folia).
     */
    @Override
    public void runTask(Runnable runnable) {
        this.player.getScheduler().run(AdvancedPortalsPlugin.getInstance(),
                                       (task) -> runnable.run(), null);
    }

    @Override
    public void sendActionBar(String message) {
        player.sendActionBar(PaperText.legacy(message));
    }

    /**
     * Shown with the Paper dialog API. Servers older than 1.21.6 don't have
     * it, in that case the command falls back to its text help.
     */
    @Override
    public boolean showEditor(EditorForm form) {
        try {
            PaperDialogs.show(this.player, this, form);
            return true;
        } catch (LinkageError e) {
            return false;
        }
    }

    @Override
    public boolean highlightRegion(BlockLocation min, BlockLocation max,
                                   int ticks) {
        try {
            return PaperHighlights.region(this.player, min, max, ticks);
        } catch (LinkageError e) {
            return false;
        }
    }

    @Override
    public boolean highlightPoint(PlayerLocation location, int ticks) {
        try {
            return PaperHighlights.point(this.player, location, ticks);
        } catch (LinkageError e) {
            return false;
        }
    }

    @Override
    public boolean isOp() {
        return this.player.isOp();
    }

    @Override
    public boolean hasPermission(String permission) {
        return this.player.hasPermission(permission);
    }

    @Override
    public void giveItem(String material, String itemName,
                         String... itemDescription) {
        ItemStack regionselector =
            new ItemStack(Material.getMaterial(material));
        ItemMeta selectorname = regionselector.getItemMeta();
        selectorname.setDisplayName(itemName);
        selectorname.setLore(Arrays.asList(itemDescription));
        regionselector.setItemMeta(selectorname);
        this.player.getInventory().addItem(regionselector);
    }

    @Override
    public boolean sendPacket(String channel, byte[] bytes) {
        player.sendPluginMessage(AdvancedPortalsPlugin.getInstance(), channel,
                                 bytes);
        return true;
    }

    @Override
    public GameMode getGameMode() {
        try {
            return GameMode.valueOf(this.player.getGameMode().name());
        } catch (IllegalArgumentException e) {
            return GameMode.SURVIVAL;
        }
    }

    public Player getPlayer() {
        return this.player;
    }

    @Override
    public void playSound(String sound, float volume, float pitch) {
        this.player.playSound(this.player.getLocation(), sound, volume, pitch);
    }

    @Override
    public ServerContainer getServer() {
        return new FoliaServerContainer(this.player.getServer());
    }

    @Override
    public void spawnColoredDust(Vector position, double xSpread,
                                 double ySpread, double zSpread, int count,
                                 Color color) {
        // Check distance to player (squared, so no square root or extra
        // Location per particle)
        Location playerLoc = this.player.getLocation();
        double dx = playerLoc.getX() - position.getX();
        double dy = playerLoc.getY() - position.getY();
        double dz = playerLoc.getZ() - position.getZ();
        if (dx * dx + dy * dy + dz * dz > 180 * 180) {
            return;
        }

        Particle.DustOptions dustOptions = new Particle.DustOptions(
            org.bukkit.Color.fromRGB(color.getRed(), color.getGreen(),
                                     color.getBlue()),
            1.5f);
        this.player.spawnParticle(Particle.DUST, position.getX(),
                                  position.getY(), position.getZ(), count,
                                  xSpread, ySpread, zSpread, count,
                                  dustOptions);
    }
}
