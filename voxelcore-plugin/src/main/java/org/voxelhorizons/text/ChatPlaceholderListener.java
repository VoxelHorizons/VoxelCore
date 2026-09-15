package org.voxelhorizons.text;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/** Applies font placeholders to player chat without moving work onto the server thread. */
@SuppressWarnings("deprecation")
public final class ChatPlaceholderListener implements Listener {
    public static final String INLINE_PERMISSION = "voxelcore.placeholders.chat";
    public static final String GUI_PERMISSION = "voxelcore.placeholders.chat.gui";

    private final TextPlaceholderService placeholders;

    public ChatPlaceholderListener(TextPlaceholderService placeholders) {
        if (placeholders == null) throw new IllegalArgumentException("placeholders cannot be null");
        this.placeholders = placeholders;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        boolean allowInline = event.getPlayer().hasPermission(INLINE_PERMISSION);
        boolean allowGui = event.getPlayer().hasPermission(GUI_PERMISSION);
        if (!allowInline && !allowGui) return;
        event.setMessage(placeholders.resolve(event.getMessage(), allowInline, allowGui));
    }
}
