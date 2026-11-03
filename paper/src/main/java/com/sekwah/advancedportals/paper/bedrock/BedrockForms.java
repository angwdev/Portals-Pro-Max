package com.sekwah.advancedportals.paper.bedrock;

import com.sekwah.advancedportals.core.connector.containers.EditorForm;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.paper.PortalsProMaxPlugin;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;

/**
 * Shows an {@link EditorForm} as a native Bedrock form.
 */
final class BedrockForms {
    private BedrockForms() {}

    static boolean show(Player player, PlayerContainer container,
                        EditorForm form) {
        CustomForm.Builder builder = CustomForm.builder().title(form.title);
        // The label takes the first slot, the inputs follow it
        builder.label(form.hint);
        for (EditorForm.Field field : form.fields) {
            builder.input(field.label, field.initial, field.initial);
        }
        builder.validResultHandler(response -> {
            Map<String, String> values = new HashMap<>();
            for (int i = 0; i < form.fields.size(); i++) {
                EditorForm.Field field = form.fields.get(i);
                String text = response.asInput(i + 1);
                values.put(field.name, text == null ? field.initial : text);
            }
            // Form responses arrive on a network thread, go back to the
            // thread that owns the player (required on Folia)
            player.getScheduler().run(
                PortalsProMaxPlugin.getInstance(),
                task -> form.onSubmit.accept(container, values), null);
        });
        return BedrockSupport.send(player, builder.build());
    }
}
