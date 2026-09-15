package org.voxelhorizons.text;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/** Applies font placeholders to player chat without moving work onto the server thread. */
@SuppressWarnings("deprecation")
public final class ChatPlaceholderListener implements Listener {
    public static final String PERMISSION = "voxelcore.placeholders.chat";

    private final TextPlaceholderService placeholders;

    public ChatPlaceholderListener(TextPlaceholderService placeholders) {
        if (placeholders == null) throw new IllegalArgumentException("placeholders cannot be null");
        this.placeholders = placeholders;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().hasPermission(PERMISSION)) return;
        event.setMessage(placeholders.resolve(event.getMessage()));
    }
}
