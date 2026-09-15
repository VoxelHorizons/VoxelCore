package org.voxelhorizons.text;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;

/** Applies font placeholders to player chat and server-authored legacy chat formatting. */
@SuppressWarnings("deprecation")
public final class ChatPlaceholderListener implements Listener {
    public static final String INLINE_PERMISSION = "voxelcore.placeholders.chat";
    public static final String GUI_PERMISSION = "voxelcore.placeholders.chat.gui";

    private final TextPlaceholderService placeholders;

    public ChatPlaceholderListener(TextPlaceholderService placeholders) {
        if (placeholders == null) throw new IllegalArgumentException("placeholders cannot be null");
        this.placeholders = placeholders;
    }

    /**
     * Player-authored text remains permission-gated. This executes before normal chat formatters so
     * plugins such as LPC receive the already-resolved message when the sender is authorized.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        boolean allowInline = player.hasPermission(INLINE_PERMISSION);
        boolean allowGui = player.hasPermission(GUI_PERMISSION);
        if (!allowInline && !allowGui) return;
        event.setMessage(placeholders.resolve(event.getMessage(), allowInline, allowGui));
    }

    /**
     * Resolves server-authored formatting after prefix/suffix plugins have populated the final
     * legacy format. LuckPerms metadata itself is not player-authored, so prefixes such as
     * {@code :owner:} are resolved regardless of the sender's chat-placeholder permission.
     *
     * <p>MONITOR is intentionally used here because chat formatters commonly run at HIGHEST. When
     * the player's message still contains a VoxelCore placeholder they are not allowed to use, the
     * message portion is temporarily protected before resolving the surrounding server format so
     * an unrestricted prefix pass cannot become a permission bypass.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFormattedPlayerChat(AsyncPlayerChatEvent event) {
        String format = event.getFormat();
        if (format == null || format.indexOf(':') < 0) return;

        Player player = event.getPlayer();
        boolean allowInline = player.hasPermission(INLINE_PERMISSION);
        boolean allowGui = player.hasPermission(GUI_PERMISSION);
        String message = event.getMessage();

        String restrictedMessage = placeholders.resolve(message, allowInline, allowGui);
        String unrestrictedMessage = placeholders.resolve(message);
        boolean containsBlockedPlayerPlaceholder = !safeEquals(restrictedMessage, unrestrictedMessage);

        if (!containsBlockedPlayerPlaceholder) {
            String resolved = placeholders.resolve(format);
            if (!safeEquals(format, resolved)) event.setFormat(resolved);
            return;
        }

        ProtectedFormat protectedFormat = protectPlayerMessage(format, message);
        if (protectedFormat == null) {
            // A formatter transformed the player message beyond the common legacy forms below.
            // Do not resolve the whole final format because that could resolve a placeholder the
            // player is not permitted to type. Server prefixes still resolve on ordinary messages.
            return;
        }

        String resolved = placeholders.resolve(protectedFormat.format);
        resolved = resolved.replace(protectedFormat.marker, protectedFormat.message);
        if (!safeEquals(format, resolved)) event.setFormat(resolved);
    }

    private static ProtectedFormat protectPlayerMessage(String format, String message) {
        if (message == null || message.isEmpty()) return null;

        ProtectedFormat exact = protectLastOccurrence(format, message);
        if (exact != null) return exact;

        String translated = ChatColor.translateAlternateColorCodes('&', message);
        String stripped = ChatColor.stripColor(translated);
        if (stripped != null && !stripped.isEmpty() && !stripped.equals(message)) {
            ProtectedFormat normalized = protectLastOccurrence(format, stripped);
            if (normalized != null) return normalized;
        }
        return null;
    }

    private static ProtectedFormat protectLastOccurrence(String format, String message) {
        int index = format.lastIndexOf(message);
        if (index < 0) return null;
        String marker = "__VOXELCORE_PLAYER_MESSAGE_" + UUID.randomUUID().toString().replace("-", "") + "__";
        String protectedText = format.substring(0, index) + marker + format.substring(index + message.length());
        return new ProtectedFormat(protectedText, marker, message);
    }

    private static boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static final class ProtectedFormat {
        private final String format;
        private final String marker;
        private final String message;

        private ProtectedFormat(String format, String marker, String message) {
            this.format = format;
            this.marker = marker;
            this.message = message;
        }
    }
}
