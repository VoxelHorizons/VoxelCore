package org.voxelhorizons.text;

import java.util.Locale;

/** Converts PlaceholderAPI parameters into VoxelCore's existing font aliases. */
final class PlaceholderApiFontResolver {
    private static final String PREFIX = "font_";

    private PlaceholderApiFontResolver() {}

    static String resolve(String parameters, TextPlaceholderService placeholders) {
        String alias = alias(parameters);
        if (alias == null) return null;
        String resolved = placeholders.resolve(alias);
        return alias.equals(resolved) ? null : resolved;
    }

    static String alias(String parameters) {
        if (parameters == null) return null;
        String normalized = parameters.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(PREFIX) || normalized.length() == PREFIX.length()) return null;
        String name = normalized.substring(PREFIX.length());
        if (name.contains("..") || !name.matches("[a-z0-9_.-]+(?:/[a-z0-9_.-]+)?")) return null;
        return ":" + name + ":";
    }
}
