package org.voxelhorizons.block;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.block.BlockDefinition;

import java.util.List;
import java.util.Locale;

/** Matches configured vanilla tool categories, exact materials, and custom item IDs. */
final class BlockToolMatcher {
    private BlockToolMatcher() { }

    static boolean canHarvest(BlockDefinition block, String material, ContentID customItem) {
        return canHarvest(block.breakTools(), block.minimumToolTier(), material,
                customItem == null ? null : customItem.toString());
    }

    static boolean canHarvest(List<String> whitelist, String minimumTier, String material, String customItem) {
        String held = normalizeMaterial(material);
        boolean matched = whitelist == null || whitelist.isEmpty();
        if (!matched) {
            for (String configured : whitelist) {
                String rule = configured.trim().toUpperCase(Locale.ROOT);
                if (isCategory(rule) && categoryMatches(rule, held)) matched = true;
                else if (rule.indexOf(':') >= 0 && !rule.startsWith("MINECRAFT:")) {
                    matched = customItem != null && rule.equals(customItem.toUpperCase(Locale.ROOT));
                } else if (normalizeMaterial(rule).equals(held)) matched = true;
                if (matched) break;
            }
        }
        if (!matched) return false;
        return minimumTier == null || tier(held) >= tierMinimum(minimumTier);
    }

    private static boolean isCategory(String value) {
        return "PICKAXE".equals(value) || "AXE".equals(value) || "SHOVEL".equals(value)
                || "HOE".equals(value) || "SWORD".equals(value) || "SHEARS".equals(value)
                || "HAND".equals(value);
    }

    private static boolean categoryMatches(String category, String material) {
        if ("HAND".equals(category)) return "AIR".equals(material);
        if ("SHEARS".equals(category)) return "SHEARS".equals(material);
        return material.endsWith("_" + category);
    }

    private static String normalizeMaterial(String value) {
        if (value == null || value.trim().isEmpty()) return "AIR";
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("MINECRAFT:") ? normalized.substring("MINECRAFT:".length()) : normalized;
    }

    private static int tier(String material) {
        if (material.startsWith("NETHERITE_")) return 4;
        if (material.startsWith("DIAMOND_")) return 3;
        if (material.startsWith("IRON_")) return 2;
        if (material.startsWith("STONE_")) return 1;
        if (material.startsWith("WOOD_") || material.startsWith("WOODEN_")
                || material.startsWith("GOLD_") || material.startsWith("GOLDEN_")) return 0;
        return -1;
    }

    private static int tierMinimum(String tier) {
        String normalized = tier.toUpperCase(Locale.ROOT);
        if ("NETHERITE".equals(normalized)) return 4;
        if ("DIAMOND".equals(normalized)) return 3;
        if ("IRON".equals(normalized)) return 2;
        if ("STONE".equals(normalized)) return 1;
        return 0;
    }
}
