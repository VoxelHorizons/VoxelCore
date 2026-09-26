package org.voxelhorizons.text;

import org.bukkit.block.Chest;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.block.DoubleChest;
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
    private volatile boolean chestTitlesEnabled;
    private volatile String singleChestTitle;
    private volatile String doubleChestTitle;

    private final Method titleOverrideMethod;
    private final Object legacySectionSerializer;
    private final Method legacyDeserializeMethod;
    private boolean warnedUnavailable;
    private boolean warnedFailure;

    public InventoryTitlePlaceholderListener(Plugin plugin, TextPlaceholderService placeholders) {
        this(plugin, placeholders, false, "", "");
    }

    public InventoryTitlePlaceholderListener(Plugin plugin, TextPlaceholderService placeholders,
                                             boolean chestTitlesEnabled,
                                             String singleChestTitle,
                                             String doubleChestTitle) {
        if (plugin == null) throw new IllegalArgumentException("plugin cannot be null");
        if (placeholders == null) throw new IllegalArgumentException("placeholders cannot be null");
        this.plugin = plugin;
        this.placeholders = placeholders;
        updateChestTitles(chestTitlesEnabled, singleChestTitle, doubleChestTitle);
        this.setTitleMethod = findSetTitleMethod();

        PaperTitleOverrideSupport paper = findPaperTitleOverrideSupport();
        this.titleOverrideMethod = paper == null ? null : paper.titleOverrideMethod;
        this.legacySectionSerializer = paper == null ? null : paper.serializer;
        this.legacyDeserializeMethod = paper == null ? null : paper.deserializeMethod;
    }

    public void updateChestTitles(boolean enabled, String singleTitle, String doubleTitle) {
        this.chestTitlesEnabled = enabled;
        this.singleChestTitle = singleTitle == null ? "" : singleTitle;
        this.doubleChestTitle = doubleTitle == null ? "" : doubleTitle;
    }

    public boolean supported() {
        return setTitleMethod != null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        final HumanEntity player = event.getPlayer();
        final Inventory openedTop = event.getInventory();
        final String originalTitle = event.getView().getTitle();
        final String configuredChestTitle = chestTitle(openedTop);
        final String titleInput = configuredChestTitle == null ? originalTitle : configuredChestTitle;
        final String resolvedTitle = placeholders.resolve(titleInput);

        if (resolvedTitle == null || resolvedTitle.equals(originalTitle)) return;

        // Paper 1.20.2+ exposes InventoryOpenEvent#titleOverride(Component). This value is used
        // in the initial open-screen packet, so the client never renders the original title first.
        if (applyInitialTitleOverride(event, resolvedTitle)) return;

        // Older Bukkit/Paper versions have no pre-open title override. Retain the legacy
        // one-tick setTitle fallback for compatibility, accepting that those versions may flash.
        if (setTitleMethod == null) {
            warnUnavailableOnce();
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                InventoryView currentView = player.getOpenInventory();
                if (currentView == null || !sameInventory(currentView.getTopInventory(), openedTop)) return;

                // Another plugin may have changed the title after the open event. Resolve that newer
                // title too rather than blindly restoring the event-time value.
                String currentTitle = currentView.getTitle();
                String configured = chestTitle(openedTop);
                String titleToApply = placeholders.resolve(configured == null ? currentTitle : configured);
                if (titleToApply == null || titleToApply.equals(currentTitle)) return;

                try {
                    setTitleMethod.invoke(currentView, titleToApply);
                } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException exception) {
                    warnFailureOnce(exception);
                }
            }
        });
    }

    private String chestTitle(Inventory inventory) {
        if (!chestTitlesEnabled || inventory == null) return null;
        Object holder = inventory.getHolder();
        if (!(holder instanceof Chest) && !(holder instanceof DoubleChest)) return null;
        String configured;
        if (inventory.getSize() == 27) configured = singleChestTitle;
        else if (inventory.getSize() == 54) configured = doubleChestTitle;
        else return null;
        return configured == null || configured.isEmpty() ? null : configured;
    }

    private boolean applyInitialTitleOverride(InventoryOpenEvent event, String title) {
        if (titleOverrideMethod == null || legacySectionSerializer == null || legacyDeserializeMethod == null) {
            return false;
        }
        try {
            Object component = legacyDeserializeMethod.invoke(legacySectionSerializer, title);
            titleOverrideMethod.invoke(event, component);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException exception) {
            warnFailureOnce(exception);
            return false;
        }
    }

    private static boolean sameInventory(Inventory current, Inventory opened) {
        return current == opened || (current != null && current.equals(opened));
    }

    private static PaperTitleOverrideSupport findPaperTitleOverrideSupport() {
        try {
            Class<?> componentType = Class.forName("net.kyori.adventure.text.Component");
            Method override = InventoryOpenEvent.class.getMethod("titleOverride", componentType);
            Class<?> serializerType = Class.forName(
                    "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            Object serializer = serializerType.getMethod("legacySection").invoke(null);
            Method deserialize = serializerType.getMethod("deserialize", String.class);
            return new PaperTitleOverrideSupport(override, serializer, deserialize);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                 | InvocationTargetException ignored) {
            return null;
        }
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

    private static final class PaperTitleOverrideSupport {
        private final Method titleOverrideMethod;
        private final Object serializer;
        private final Method deserializeMethod;

        private PaperTitleOverrideSupport(Method titleOverrideMethod, Object serializer, Method deserializeMethod) {
            this.titleOverrideMethod = titleOverrideMethod;
            this.serializer = serializer;
            this.deserializeMethod = deserializeMethod;
        }
    }

    private void warnFailureOnce(Exception exception) {
        if (warnedFailure) return;
        warnedFailure = true;
        plugin.getLogger().warning("Unable to apply resolved inventory title placeholders: " + exception.getMessage());
    }
}
