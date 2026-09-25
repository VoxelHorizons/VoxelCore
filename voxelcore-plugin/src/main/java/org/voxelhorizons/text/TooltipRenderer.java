package org.voxelhorizons.text;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.voxelhorizons.pack.TooltipGlyphs;

/** Runtime renderer for compiler-owned three-line resource-pack popup tooltips. */
public final class TooltipRenderer {
    private final TextPlaceholderService placeholders;

    public TooltipRenderer(TextPlaceholderService placeholders) {
        if (placeholders == null) throw new IllegalArgumentException("placeholders cannot be null");
        this.placeholders = placeholders;
    }

    public void show(Player player, String line1, String line2, String line3, int ticks) {
        show(player, line1, line2, line3, ticks, TooltipGlyphs.DEFAULT_X_OFFSET);
    }

    public void show(Player player, String line1, String line2, String line3, int ticks, int xOffset) {
        if (player == null) throw new IllegalArgumentException("player cannot be null");
        if (ticks < 1) throw new IllegalArgumentException("ticks must be at least 1");

        String first = prepare(line1);
        String second = prepare(line2);
        String third = prepare(line3);
        String subtitle = TooltipGlyphs.compose(first, second, third, xOffset);
        player.sendTitle("", subtitle, 0, ticks, 0);
    }

    public void clear(Player player) {
        if (player != null) player.resetTitle();
    }

    private String prepare(String input) {
        String value = input == null ? "" : ChatColor.translateAlternateColorCodes('&', input);
        return placeholders.resolve(value);
    }
}
