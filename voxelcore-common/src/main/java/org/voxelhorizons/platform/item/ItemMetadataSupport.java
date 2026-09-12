package org.voxelhorizons.platform.item;

import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.voxelhorizons.content.item.ItemRenderDefinition;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ItemMetadataSupport {
    private static final Map<String, String> LEGACY_FLAG_ALIASES;

    static {
        Map<String, String> aliases = new HashMap<String, String>();
        aliases.put("HIDE_ENCHANTMENTS", "HIDE_ENCHANTS");
        aliases.put("HIDE_DESTROYABLE", "HIDE_DESTROYS");
        aliases.put("HIDE_PLACEABLE", "HIDE_PLACED_ON");
        LEGACY_FLAG_ALIASES = Collections.unmodifiableMap(aliases);
    }

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

    /**
     * Resolves configured item-flag names directly against the Bukkit ItemFlag enum available on
     * the running server. This intentionally avoids maintaining a hard-coded list so newly added
     * Bukkit flags automatically become available to content definitions without a VoxelCore code
     * change. Unsupported flags are ignored on older Minecraft versions.
     */
    static ItemFlag resolveFlag(String configured) {
        if (configured == null) return null;
        String normalized = configured.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;

        ItemFlag direct = valueOf(normalized);
        if (direct != null) return direct;

        String legacyName = LEGACY_FLAG_ALIASES.get(normalized);
        return legacyName == null ? null : valueOf(legacyName);
    }

    private static ItemFlag valueOf(String name) {
        try {
            return ItemFlag.valueOf(name);
        } catch (IllegalArgumentException unsupportedOnThisVersion) {
            return null;
        }
    }
}
