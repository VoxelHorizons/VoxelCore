package org.voxelhorizons.text;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.voxelhorizons.pack.TooltipLayout;
import org.voxelhorizons.pack.UiGlyphRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Named popup styles backed by normal UI glyphs and editable configuration. */
public final class TooltipProfiles {
    private static final String ROOT = "ui.tooltips";
    public static final String DEFAULT = "default";

    private TooltipProfiles() {}

    /** Exposes actual named profiles for the command's tab completion. */
    public static List<String> names(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection(ROOT);
        if (section == null) return Collections.emptyList();
        Set<String> names = new TreeSet<String>();
        for (String key : section.getKeys(false)) {
            if (section.isConfigurationSection(key)) names.add(key);
        }
        return Collections.unmodifiableList(new ArrayList<String>(names));
    }

    public static TooltipLayout layout(FileConfiguration config, UiGlyphRegistry glyphs, String name) {
        String selected = normalizedName(name);
        ConfigurationSection section = config.getConfigurationSection(ROOT + "." + selected);
        if (section == null) {
            throw new IllegalArgumentException("Unknown tooltip '" + selected
                    + "'. Available: " + String.join(", ", names(config)));
        }
        return new TooltipLayout(glyphs,
                glyphId(config, selected, "left"),
                glyphId(config, selected, "center"),
                glyphId(config, selected, "right"),
                number(config, selected, "tile_overlap", 1),
                number(config, selected, "horizontal_padding", 4),
                number(config, selected, "x_offset", 55));
    }

    private static String normalizedName(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Tooltip ID must use letters, digits, underscores or hyphens");
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private static String glyphId(FileConfiguration config, String name, String key) {
        String path = ROOT + "." + name + "." + key;
        String fallback = ROOT + "." + DEFAULT + "." + key;
        String result = config.isSet(path) ? config.getString(path) : config.getString(fallback);
        if (result == null || result.trim().isEmpty()) {
            throw new IllegalArgumentException("Tooltip '" + name + "' has no " + key
                    + " UI glyph (configure " + path + ")");
        }
        return result;
    }

    private static int number(FileConfiguration config, String name, String key, int fallback) {
        String path = ROOT + "." + name + "." + key;
        String defaultPath = ROOT + "." + DEFAULT + "." + key;
        String effective = config.isSet(path) ? path : defaultPath;
        if (!config.isSet(effective)) return fallback;
        if (!config.isInt(effective)) throw new IllegalArgumentException("Tooltip " + effective + " must be an integer");
        return config.getInt(effective);
    }
}
