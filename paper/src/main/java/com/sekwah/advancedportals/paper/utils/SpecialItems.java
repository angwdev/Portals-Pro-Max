package com.sekwah.advancedportals.paper.utils;

import com.sekwah.advancedportals.paper.PortalsProMaxPlugin;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The selector axe and block placer items are recognised by a tag stored in
 * the item itself, not by the text of their name. Renaming one in an anvil
 * (or a Bedrock client dropping the formatting) no longer breaks it.
 */
public final class SpecialItems {
    /**
     * Names the items had before they were tagged, so items given by older
     * versions keep working.
     */
    private static final Map<String, String> UNTAGGED_NAMES = Map.of(
        "Portal Region Selector", "§ePortal Region Selector",
        "Portal Block Placer", "§5Portal Block Placer",
        "End Portal Block Placer", "§8End Portal Block Placer",
        "Gateway Block Placer", "§8Gateway Block Placer");

    private SpecialItems() {}

    public static NamespacedKey key() {
        return new NamespacedKey(PortalsProMaxPlugin.getInstance(), "item_id");
    }

    /**
     * @return the id the core uses for the item ("" for an item that isn't
     *     special, null if there is no item)
     */
    public static String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        String id =
            meta.getPersistentDataContainer().get(key(), PersistentDataType.STRING);
        if (id != null) {
            return id;
        }
        Component name = meta.displayName();
        if (name == null) {
            return "";
        }
        return UNTAGGED_NAMES.getOrDefault(
            PlainTextComponentSerializer.plainText().serialize(name), "");
    }
}
