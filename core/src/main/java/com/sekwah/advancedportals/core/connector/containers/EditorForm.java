package com.sekwah.advancedportals.core.connector.containers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A platform independent description of a form with a text field per value.
 * Platforms that can show one (Paper dialogs) display it and call
 * {@link #onSubmit} with the text of every field when the player saves.
 */
public final class EditorForm {
    public static final class Field {
        public final String name;
        public final String label;
        public final String initial;

        public Field(String name, String label, String initial) {
            this.name = name;
            this.label = label;
            this.initial = initial == null ? "" : initial;
        }
    }

    public final String title;
    public final String hint;
    public final String saveLabel;
    public final String cancelLabel;
    public final List<Field> fields = new ArrayList<>();

    /**
     * Called with the player and the text of every field by field name. Always
     * run on the thread that owns the player.
     */
    public final BiConsumer<PlayerContainer, Map<String, String>> onSubmit;

    public EditorForm(String title, String hint, String saveLabel,
                      String cancelLabel,
                      BiConsumer<PlayerContainer, Map<String, String>> onSubmit) {
        this.title = title;
        this.hint = hint;
        this.saveLabel = saveLabel;
        this.cancelLabel = cancelLabel;
        this.onSubmit = onSubmit;
    }
}
