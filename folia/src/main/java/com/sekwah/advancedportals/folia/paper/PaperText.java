package com.sekwah.advancedportals.folia.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Converts the legacy (section sign) strings the core produces into Adventure
 * components, including the hex colours (&#rrggbb) that the core converts to
 * the section sign "x" format.
 */
public final class PaperText {
    private static final LegacyComponentSerializer LEGACY =
        LegacyComponentSerializer.builder()
            .character('\u00A7')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private PaperText() {}

    public static Component legacy(String text) {
        return LEGACY.deserialize(text);
    }
}
