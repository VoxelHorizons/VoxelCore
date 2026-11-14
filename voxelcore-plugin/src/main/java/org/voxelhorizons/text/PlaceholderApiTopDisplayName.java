package org.voxelhorizons.text;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Retrieves the live display name for ranked JetsAntiAFKPro players. */
final class PlaceholderApiTopDisplayName {
    private static final String PREFIX = "topdisplay_";
    private PlaceholderApiTopDisplayName() {}

    static String resolve(OfflinePlayer viewer, String parameters) {
        if (parameters == null || !parameters.startsWith(PREFIX)) return null;
        int rank;
        try {
            rank = Integer.parseInt(parameters.substring(PREFIX.length()));
        } catch (NumberFormatException ex) {
            return null;
        }
        if (rank < 1 || rank > 100) return null;
        String placeholder = "%jetsantiafkpro_top_" + rank + "_player_name%";
        String username = PlaceholderAPI.setPlaceholders(viewer, placeholder);
        if (username == null || username.trim().isEmpty()
                || username.equals(placeholder)
                || username.equalsIgnoreCase("Top player not found")
                || username.equalsIgnoreCase("Player not found")) {
            return "\u00a77Unknown";
        }
        Player online = Bukkit.getPlayerExact(username);
        // Bukkit display names are live-only. Preserve an offline entry using its username.
        return online == null ? username : online.getDisplayName();
    }
}
