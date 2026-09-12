package org.voxelhorizons.platform.item;

import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.voxelhorizons.content.item.ItemRenderDefinition;

import java.util.Locale;
import java.util.Map;

public final class ItemMetadataSupport {
    private ItemMetadataSupport() {}

    public static void applyCommon(ItemMeta meta, ItemRenderDefinition render) {
        if (meta == null || render == null) return;
        if (render.unbreakable() != null) meta.setUnbreakable(render.unbreakable().booleanValue());
        for (Map.Entry<String, Boolean> entry : render.attributes().entrySet()) {
            ItemFlag flag = resolveFlag(entry.getKey());
            if (flag == null) continue;
            if (entry.getValue().booleanValue()) meta.addItemFlags(flag);
            else meta.removeItemFlags(flag);
        }
    }

    private static ItemFlag resolveFlag(String configured) {
        String normalized = configured.trim().toLowerCase(Locale.ROOT);
        String bukkit;
        if ("hide_enchantments".equals(normalized)) bukkit = "HIDE_ENCHANTS";
        else if ("hide_destroyable".equals(normalized)) bukkit = "HIDE_DESTROYS";
        else if ("hide_placeable".equals(normalized)) bukkit = "HIDE_PLACED_ON";
        else if ("hide_unbreakable".equals(normalized)) bukkit = "HIDE_UNBREAKABLE";
        else if ("hide_enchants".equals(normalized)) bukkit = "HIDE_ENCHANTS";
        else if ("hide_potion_effects".equals(normalized)) bukkit = "HIDE_POTION_EFFECTS";
        else if ("hide_attributes".equals(normalized)) bukkit = "HIDE_ATTRIBUTES";
        else if ("hide_dye".equals(normalized)) bukkit = "HIDE_DYE";
        else bukkit = normalized.toUpperCase(Locale.ROOT);
        try {
            return ItemFlag.valueOf(bukkit);
        } catch (IllegalArgumentException unsupportedOnThisVersion) {
            return null;
        }
    }
}
