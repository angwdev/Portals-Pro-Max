package com.sekwah.advancedportals.paper.bedrock;

import com.sekwah.advancedportals.core.connector.containers.EditorForm;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Bedrock players (joining through Geyser, with or without Floodgate) can't
 * see Java dialogs or display entities, so those features have Bedrock
 * versions: native forms and particle outlines.
 *
 * <p>Everything that touches the Geyser/Floodgate APIs lives in other
 * classes so nothing here fails to load when they aren't installed.
 */
public final class BedrockSupport {
    private static final String FLOODGATE = "floodgate";
    private static final String GEYSER = "Geyser-Spigot";

    private BedrockSupport() {}

    private static boolean enabled(String plugin) {
        return Bukkit.getPluginManager().isPluginEnabled(plugin);
    }

    public static boolean isBedrock(Player player) {
        UUID uuid = player.getUniqueId();
        try {
            if (enabled(FLOODGATE) && FloodgateHook.isBedrock(uuid)) {
                return true;
            }
            if (enabled(GEYSER) && GeyserHook.isBedrock(uuid)) {
                return true;
            }
        } catch (LinkageError | RuntimeException e) {
            // Broken or incompatible Geyser/Floodgate, treat as Java
        }
        return false;
    }

    /**
     * Sends a form to a Bedrock player.
     *
     * @return false if it couldn't be sent
     */
    public static boolean showEditor(Player player, PlayerContainer container,
                                     EditorForm form) {
        try {
            return BedrockForms.show(player, container, form);
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    static boolean send(Player player, org.geysermc.cumulus.form.Form form) {
        UUID uuid = player.getUniqueId();
        if (enabled(FLOODGATE) && FloodgateHook.isBedrock(uuid)) {
            return FloodgateHook.sendForm(uuid, form);
        }
        if (enabled(GEYSER) && GeyserHook.isBedrock(uuid)) {
            return GeyserHook.sendForm(uuid, form);
        }
        return false;
    }
}
