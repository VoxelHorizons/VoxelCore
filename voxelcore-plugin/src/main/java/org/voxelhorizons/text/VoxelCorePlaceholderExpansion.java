package org.voxelhorizons.text;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.voxelhorizons.VoxelCore;

/** Internal PlaceholderAPI expansion exposed only when PlaceholderAPI is installed. */
public final class VoxelCorePlaceholderExpansion extends PlaceholderExpansion {
    private final VoxelCore plugin;
    private final TextPlaceholderService placeholders;

    public VoxelCorePlaceholderExpansion(VoxelCore plugin, TextPlaceholderService placeholders) {
        this.plugin = plugin;
        this.placeholders = placeholders;
    }

    @Override public String getIdentifier() { return "voxelcore"; }
    @Override public String getAuthor() { return "VoxelHorizons"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String parameters) {
        return PlaceholderApiFontResolver.resolve(parameters, placeholders);
    }
}
