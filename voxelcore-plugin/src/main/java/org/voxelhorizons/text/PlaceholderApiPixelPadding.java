package org.voxelhorizons.text;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;
import org.voxelhorizons.pack.UiSpacingGlyphs;

/** Pixel-aware right padding for text displays using VoxelCore's existing spacing glyphs. */
final class PlaceholderApiPixelPadding {
    private static final String PREFIX = "pad_";
    private PlaceholderApiPixelPadding() {}

    static String resolve(OfflinePlayer viewer, String parameters) {
        if (parameters == null || !parameters.startsWith(PREFIX)) return null;
        int separator = parameters.indexOf('_', PREFIX.length());
        if (separator < 0) return null;
        int target;
        try {
            target = Integer.parseInt(parameters.substring(PREFIX.length(), separator));
        } catch (NumberFormatException ex) {
            return null;
        }
        if (target < 0 || target > 1024) return null;
        String source = parameters.substring(separator + 1);
        if (source.length() > 1024) return null;
        // Braced PlaceholderAPI syntax lets the full nested placeholder resolve first.
        String text = PlaceholderAPI.setBracketPlaceholders(viewer, source);
        return pad(text, target);
    }

    static String pad(String text, int target) {
        if (text == null) text = "";
        int width = MinecraftTextPixelWidth.width(text);
        return text + UiSpacingGlyphs.charactersForOffset(Math.max(0, target - width));
    }
}
