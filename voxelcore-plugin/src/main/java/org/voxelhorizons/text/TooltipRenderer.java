package org.voxelhorizons.text;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.voxelhorizons.pack.TooltipLayout;
import org.voxelhorizons.pack.UiGlyphRegistry;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.function.Supplier;

/** Runtime renderer for compiler-owned three-line resource-pack popup tooltips. */
public final class TooltipRenderer {
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

    public void show(Player player, String line1, String line2, String line3, int ticks) {
        show(player, line1, line2, line3, ticks, settings.get().getInt("ui.tooltip.x_offset", 55));
    }

    public void show(Player player, String line1, String line2, String line3, int ticks, int xOffset) {
        if (player == null) throw new IllegalArgumentException("player cannot be null");
        if (ticks < 1) throw new IllegalArgumentException("ticks must be at least 1");

        String first = prepare(line1);
        String second = prepare(line2);
        String third = prepare(line3);
        FileConfiguration config = settings.get();
        String prefix = "ui.tooltip.";
        TooltipLayout layout = new TooltipLayout(glyphs,
                config.getString(prefix + "left", "voxel:tooltip_left"),
                config.getString(prefix + "center", "voxel:tooltip_center"),
                config.getString(prefix + "right", "voxel:tooltip_right"),
                config.getInt(prefix + "tile_overlap", 1),
                config.getInt(prefix + "horizontal_padding", 4), xOffset);
        String subtitle = layout.compose(first, second, third);
        player.sendTitle("", subtitle, 0, ticks, 0);
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
