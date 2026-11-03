package com.sekwah.advancedportals.paper.bedrock;

import java.util.UUID;
import org.geysermc.cumulus.form.Form;
import org.geysermc.floodgate.api.FloodgateApi;

/**
 * Only ever loaded when the Floodgate plugin is installed.
 */
final class FloodgateHook {
    private FloodgateHook() {}

    static boolean isBedrock(UUID uuid) {
        return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
    }

    static boolean sendForm(UUID uuid, Form form) {
        return FloodgateApi.getInstance().sendForm(uuid, form);
    }
}
