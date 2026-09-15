package org.voxelhorizons.text;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Resolves VoxelCore UI placeholders in inventory titles created by VoxelCore or third-party plugins.
 *
 * <p>The plugin module compiles against the 1.12 Bukkit API, where {@code InventoryView#setTitle}
 * does not exist. Newer servers expose that method, so this listener discovers it reflectively and
 * updates the already-open view one tick after {@link InventoryOpenEvent}. This preserves the
 * original inventory holder, contents, click handlers, and third-party menu session state.</p>
 */
public final class InventoryTitlePlaceholderListener implements Listener {
    private final Plugin plugin;
    private final TextPlaceholderService placeholders;
    private final Method setTitleMethod;
    private boolean warnedUnavailable;
    private boolean warnedFailure;

    public InventoryTitlePlaceholderListener(Plugin plugin, TextPlaceholderService placeholders) {
        if (plugin == null) throw new IllegalArgumentException("plugin cannot be null");
        if (placeholders == null) throw new IllegalArgumentException("placeholders cannot be null");
        this.plugin = plugin;
        this.placeholders = placeholders;
        this.setTitleMethod = findSetTitleMethod();
    }

    public boolean supported() {
        return setTitleMethod != null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (setTitleMethod == null) {
            warnUnavailableOnce();
            return;
        }

        final HumanEntity player = event.getPlayer();
        final Inventory openedTop = event.getInventory();
        final String originalTitle = event.getView().getTitle();
        final String resolvedTitle = placeholders.resolve(originalTitle);

        if (resolvedTitle == null || resolvedTitle.equals(originalTitle)) return;

        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                InventoryView currentView = player.getOpenInventory();
                if (currentView == null || !sameInventory(currentView.getTopInventory(), openedTop)) return;

                // Another plugin may have changed the title after the open event. Resolve that newer
                // title too rather than blindly restoring the event-time value.
                String currentTitle = currentView.getTitle();
                String titleToApply = placeholders.resolve(currentTitle);
                if (titleToApply == null || titleToApply.equals(currentTitle)) return;

                try {
                    setTitleMethod.invoke(currentView, titleToApply);
                } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException exception) {
                    warnFailureOnce(exception);
                }
            }
        });
    }

    private static boolean sameInventory(Inventory current, Inventory opened) {
        return current == opened || (current != null && current.equals(opened));
    }

    private static Method findSetTitleMethod() {
        try {
            return InventoryView.class.getMethod("setTitle", String.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private void warnUnavailableOnce() {
        if (warnedUnavailable) return;
        warnedUnavailable = true;
        plugin.getLogger().warning("Inventory title placeholder injection is unavailable on this server version; "
                + "chat and explicit TextPlaceholderService resolution remain available.");
    }

    private void warnFailureOnce(Exception exception) {
        if (warnedFailure) return;
        warnedFailure = true;
        plugin.getLogger().warning("Unable to apply resolved inventory title placeholders: " + exception.getMessage());
    }
}
