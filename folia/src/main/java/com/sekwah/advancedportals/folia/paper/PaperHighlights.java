package com.sekwah.advancedportals.folia.paper;

import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.folia.AdvancedPortalsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Glowing boxes that only one player can see, made with block display
 * entities. They are not persistent and remove themselves when the time is up,
 * so nothing is left behind if the server stops.
 */
public final class PaperHighlights {
    /** Don't spawn things in places that are probably not loaded */
    private static final double MAX_DISTANCE = 160;

    private PaperHighlights() {}

    public static boolean region(Player player, BlockLocation min,
                                 BlockLocation max, int ticks) {
        World world = player.getWorld();
        if (!world.getName().equals(min.getWorldName())) {
            return false;
        }
        double sizeX = max.getPosX() - min.getPosX() + 1;
        double sizeY = max.getPosY() - min.getPosY() + 1;
        double sizeZ = max.getPosZ() - min.getPosZ() + 1;
        Location origin =
            new Location(world, min.getPosX(), min.getPosY(), min.getPosZ());
        if (tooFar(player, origin.getX() + sizeX / 2, origin.getZ() + sizeZ / 2)) {
            return false;
        }
        spawn(player, origin, (float) sizeX, (float) sizeY, (float) sizeZ,
              Material.LIGHT_BLUE_STAINED_GLASS, Color.AQUA, ticks);
        return true;
    }

    /**
     * A player sized box at the exact destination position with a small
     * marker on the side the player will be facing.
     */
    public static boolean point(Player player, PlayerLocation loc, int ticks) {
        World world = player.getWorld();
        if (!world.getName().equals(loc.getWorldName())) {
            return false;
        }
        if (tooFar(player, loc.getPosX(), loc.getPosZ())) {
            return false;
        }
        spawn(player,
              new Location(world, loc.getPosX() - 0.3, loc.getPosY(),
                           loc.getPosZ() - 0.3),
              0.6f, 1.8f, 0.6f, Material.LIME_STAINED_GLASS, Color.LIME, ticks);

        // Minecraft yaw 0 faces +z, 90 faces -x
        double yaw = Math.toRadians(loc.getYaw());
        double eyeX = loc.getPosX() - Math.sin(yaw) * 0.45;
        double eyeZ = loc.getPosZ() + Math.cos(yaw) * 0.45;
        spawn(player, new Location(world, eyeX - 0.1, loc.getPosY() + 1.45, eyeZ - 0.1),
              0.2f, 0.2f, 0.2f, Material.RED_CONCRETE, Color.RED, ticks);
        return true;
    }

    private static boolean tooFar(Player player, double x, double z) {
        Location playerLoc = player.getLocation();
        double dx = playerLoc.getX() - x;
        double dz = playerLoc.getZ() - z;
        return dx * dx + dz * dz > MAX_DISTANCE * MAX_DISTANCE;
    }

    private static void spawn(Player player, Location origin, float sizeX,
                              float sizeY, float sizeZ, Material material,
                              Color glow, int ticks) {
        Plugin plugin = AdvancedPortalsPlugin.getInstance();
        // The display has to be created by the thread that owns its location
        Bukkit.getRegionScheduler().run(plugin, origin, task -> {
            BlockDisplay display = origin.getWorld().spawn(
                origin, BlockDisplay.class, spawned -> {
                    spawned.setBlock(material.createBlockData());
                    spawned.setTransformation(new Transformation(
                        new Vector3f(), new AxisAngle4f(),
                        new Vector3f(sizeX, sizeY, sizeZ), new AxisAngle4f()));
                    spawned.setBrightness(new Display.Brightness(15, 15));
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(glow);
                    spawned.setViewRange(2f);
                    spawned.setPersistent(false);
                    // Only shown to the player that asked for it
                    spawned.setVisibleByDefault(false);
                });
            display.getScheduler().runDelayed(plugin, t -> display.remove(), null,
                                              Math.max(1, ticks));
            player.getScheduler().run(
                plugin, t -> player.showEntity(plugin, display), null);
        });
    }
}
