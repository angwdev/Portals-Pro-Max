package com.sekwah.advancedportals.paper.bedrock;

import java.util.UUID;
import org.geysermc.cumulus.form.Form;
import org.geysermc.geyser.api.GeyserApi;

/**
 * Only ever loaded when the Geyser plugin is installed.
 */
final class GeyserHook {
    private GeyserHook() {}

    static boolean isBedrock(UUID uuid) {
        return GeyserApi.api().isBedrockPlayer(uuid);
    }

    static boolean sendForm(UUID uuid, Form form) {
        return GeyserApi.api().sendForm(uuid, form);
    }
}
