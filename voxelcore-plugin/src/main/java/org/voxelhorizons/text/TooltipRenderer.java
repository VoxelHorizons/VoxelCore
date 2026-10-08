package org.voxelhorizons.text;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.voxelhorizons.pack.TooltipLayout;
import org.voxelhorizons.pack.UiGlyphRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** Renders named, configurable three-line popup tooltip styles from normal UI font glyphs. */
public final class TooltipRenderer {
    public static final String DEFAULT_VARIANT = "default";

    private final TextPlaceholderService placeholders;
    private volatile UiGlyphRegistry glyphs;
    private final Supplier<FileConfiguration> settings;

    public TooltipRenderer(TextPlaceholderService placeholders, UiGlyphRegistry glyphs,
                           Supplier<FileConfiguration> settings) {
        if (placeholders == null || glyphs == null || settings == null) {
            throw new IllegalArgumentException("Tooltip dependencies cannot be null");
        }
        this.placeholders = placeholders;
        this.glyphs = glyphs;
        this.settings = settings;
    }

    /** Existing API continues to render the default tooltip. */
    public void show(Player player, String line1, String line2, String line3, int ticks) {
        show(player, DEFAULT_VARIANT, line1, line2, line3, ticks);
    }

    /** Existing API with an explicit offset continues to render the default tooltip. */
    public void show(Player player, String line1, String line2, String line3, int ticks, int xOffset) {
        render(player, DEFAULT_VARIANT, line1, line2, line3, ticks, Integer.valueOf(xOffset));
    }

    public void show(Player player, String variant, String line1, String line2, String line3, int ticks) {
        render(player, variant, line1, line2, line3, ticks, null);
    }

    public void show(Player player, String variant, String line1, String line2, String line3,
                     int ticks, int xOffset) {
        render(player, variant, line1, line2, line3, ticks, Integer.valueOf(xOffset));
    }

    public List<String> variants() {
        ConfigurationSection section = settings.get().getConfigurationSection("ui.tooltips");
        if (section == null) return Collections.singletonList(DEFAULT_VARIANT);
        List<String> ids = new ArrayList<String>(section.getKeys(false));
        Collections.sort(ids);
        return Collections.unmodifiableList(ids);
    }

    private void render(Player player, String variant, String line1, String line2, String line3,
                        int ticks, Integer overrideOffset) {
        if (player == null) throw new IllegalArgumentException("player cannot be null");
        if (ticks < 1) throw new IllegalArgumentException("ticks must be at least 1");

        FileConfiguration config = settings.get();
        String id = validateVariantId(variant);
        String path = "ui.tooltips." + id;
        ConfigurationSection variants = config.getConfigurationSection("ui.tooltips");
        if (variants == null || !variants.isConfigurationSection(id)) {
            throw new IllegalArgumentException("Unknown tooltip variant '" + id
                    + "'. Available: " + variants());
        }

        String defaults = "ui.tooltips.default.";
        String left = config.getString(path + ".left", config.getString(defaults + "left", "voxel:tooltip_left"));
        String center = config.getString(path + ".center", config.getString(defaults + "center", "voxel:tooltip_center"));
        String right = config.getString(path + ".right", config.getString(defaults + "right", "voxel:tooltip_right"));
        int overlap = config.getInt(path + ".tile_overlap", config.getInt(defaults + "tile_overlap", 1));
        int padding = config.getInt(path + ".horizontal_padding", config.getInt(defaults + "horizontal_padding", 4));
        int xOffset = overrideOffset == null
                ? config.getInt(path + ".x_offset", config.getInt(defaults + "x_offset", 55))
                : overrideOffset.intValue();

        TooltipLayout layout = new TooltipLayout(glyphs, left, center, right, overlap, padding, xOffset);
        String subtitle = layout.compose(prepare(line1), prepare(line2), prepare(line3));
        player.sendTitle("", subtitle, 0, ticks, 0);
    }

    private static String validateVariantId(String variant) {
        if (variant == null || !variant.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid tooltip variant ID '" + variant
                    + "' (use lowercase letters, numbers, _ or -)");
        }
        return variant;
    }

    public void updateGlyphs(UiGlyphRegistry updated) {
        if (updated == null) throw new IllegalArgumentException("UI glyph registry cannot be null");
        glyphs = updated;
    }

    public void clear(Player player) {
        if (player != null) player.resetTitle();
    }

    private String prepare(String input) {
        String value = input == null ? "" : ChatColor.translateAlternateColorCodes('&', input);
        return placeholders.resolve(value);
    }
}
