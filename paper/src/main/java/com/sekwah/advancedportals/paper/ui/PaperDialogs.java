package com.sekwah.advancedportals.paper.ui;

import com.sekwah.advancedportals.core.connector.containers.EditorForm;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.paper.PortalsProMaxPlugin;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

/**
 * Shows an {@link EditorForm} with the Paper dialog API (Minecraft 1.21.6+).
 *
 * <p>Kept in its own class so servers without the dialog API only fail when
 * the editor is used, not when the plugin loads.
 */
public final class PaperDialogs {
    private PaperDialogs() {}

    public static void show(Player player, PlayerContainer container,
                            EditorForm form) {
        List<DialogInput> inputs = new ArrayList<>();
        for (EditorForm.Field field : form.fields) {
            inputs.add(DialogInput.text(field.name, PaperText.legacy(field.label))
                           .width(320)
                           .initial(field.initial)
                           .maxLength(1000)
                           .build());
        }

        DialogAction save = DialogAction.customClick(
            (response, audience) -> {
                Map<String, String> values = new HashMap<>();
                for (EditorForm.Field field : form.fields) {
                    String text = response.getText(field.name);
                    values.put(field.name, text == null ? field.initial : text);
                }
                // The callback is not guaranteed to be on the thread that owns
                // the player, which is needed to touch it on Folia.
                player.getScheduler().run(
                    PortalsProMaxPlugin.getInstance(),
                    task -> form.onSubmit.accept(container, values), null);
            },
            ClickCallback.Options.builder()
                .uses(1)
                .lifetime(Duration.ofMinutes(30))
                .build());

        Dialog dialog = Dialog.create(
            builder
            -> builder.empty()
                   .base(DialogBase.builder(PaperText.legacy(form.title))
                             .body(List.of(DialogBody.plainMessage(
                                 PaperText.legacy(form.hint), 320)))
                             .inputs(inputs)
                             .build())
                   .type(DialogType.confirmation(
                       ActionButton.builder(PaperText.legacy(form.saveLabel))
                           .action(save)
                           .build(),
                       ActionButton.builder(PaperText.legacy(form.cancelLabel))
                           .build())));

        player.showDialog(dialog);
    }
}
