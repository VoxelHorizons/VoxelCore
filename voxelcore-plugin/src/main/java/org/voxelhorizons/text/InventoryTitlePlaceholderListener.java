package org.voxelhorizons.text;

import org.bukkit.ChatColor;
import org.bukkit.block.Chest;
import org.bukkit.block.BlockState;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

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
    private volatile boolean containerTitlesEnabled;
    private volatile Map<String, String> containerTitles = Collections.emptyMap();

    /** Modern Paper bridge is discovered at runtime to retain 1.12 binary compatibility. */
    private final PaperTitleOverrideSupport paperTitleSupport;
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

        this.paperTitleSupport = findPaperTitleOverrideSupport();
    }

    public void updateChestTitles(boolean enabled, String singleTitle, String doubleTitle) {
        this.chestTitlesEnabled = enabled;
        this.singleChestTitle = singleTitle == null ? "" : singleTitle;
        this.doubleChestTitle = doubleTitle == null ? "" : doubleTitle;
    }

    /**
     * Configures titles for physical block-backed inventories. Keys match Bukkit material names
     * in lower case (for example barrel, hopper, furnace), plus chest.single/chest.double.
     * No newer block API types are referenced, so this remains binary-compatible with 1.12.
     */
    public void updateContainerTitles(boolean enabled, Map<String, String> templates) {
        Map<String, String> normalized = new HashMap<String, String>();
        if (templates != null) {
            for (Map.Entry<String, String> entry : templates.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) continue;
                normalized.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
            }
        }
        containerTitles = Collections.unmodifiableMap(normalized);
        containerTitlesEnabled = enabled;
    }

    public boolean supported() {
        return setTitleMethod != null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        final HumanEntity player = event.getPlayer();
        final Inventory openedTop = event.getInventory();
        final String originalTitle = event.getView().getTitle();
        final String configuredContainerTitle = containerTitle(openedTop);
        final String titleInput = configuredContainerTitle == null ? originalTitle : configuredContainerTitle;
        final String resolvedTitle = resolveTitle(titleInput);

        if (resolvedTitle == null || resolvedTitle.equals(originalTitle)) return;

        // Paper 1.20.2+ exposes InventoryOpenEvent#titleOverride(Component). This value is used
        // in the initial open-screen packet, so the client never renders the original title first.
        if (applyInitialTitleOverride(event, configuredContainerTitle, resolvedTitle)) return;

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
                String configured = containerTitle(openedTop);
                String titleToApply = resolveTitle(configured == null ? currentTitle : configured);
                if (titleToApply == null || titleToApply.equals(currentTitle)) return;

                try {
                    setTitleMethod.invoke(currentView, titleToApply);
                } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException exception) {
                    warnFailureOnce(exception);
                }
            }
        });
    }

    private String resolveTitle(String input) {
        if (input == null) return null;
        String colored = ChatColor.translateAlternateColorCodes('&', input);
        String resolved = placeholders.resolve(colored);
        return resolved == null ? null : ChatColor.translateAlternateColorCodes('&', resolved);
    }

    private String containerTitle(Inventory inventory) {
        if (inventory == null) return null;
        return titleForPhysicalHolder(inventory.getHolder(), inventory.getSize(),
                chestTitlesEnabled, singleChestTitle, doubleChestTitle,
                containerTitlesEnabled, containerTitles);
    }

    /**
     * Only block-backed inventories are eligible. Virtual/plugin GUIs and vehicle/entity
     * inventories must retain their own titles, even when they have the same slot count.
     * Kept package-private for cross-version regression tests.
     */
    static String titleForPhysicalHolder(Object holder, int size, boolean legacyChestEnabled,
                                         String singleChest, String doubleChest,
                                         boolean typesEnabled, Map<String, String> templates) {
        if (holder instanceof Chest || holder instanceof DoubleChest) {
            String key;
            String legacy;
            if (size == 27) {
                key = "chest.single";
                legacy = singleChest;
            } else if (size == 54) {
                key = "chest.double";
                legacy = doubleChest;
            } else {
                return null;
            }
            String configured = typesEnabled ? templates.get(key) : null;
            if (configured != null && !configured.isEmpty()) return configured;
            return legacyChestEnabled && legacy != null && !legacy.isEmpty() ? legacy : null;
        }
        if (!typesEnabled || !(holder instanceof BlockState)) return null;
        String material = ((BlockState) holder).getType().name().toLowerCase(Locale.ROOT);
        String configured = templates.get(material);
        // One shulker_box template applies to all colours unless a specific colour is set.
        if (configured == null && material.endsWith("_shulker_box")) {
            configured = templates.get("shulker_box");
        }
        return configured == null || configured.isEmpty() ? null : configured;
    }

    /**
     * On modern Paper, preserve the rich title tree supplied by a menu plugin. Deserializing
     * InventoryView#getTitle() loses Adventure fonts (and click/hover/decorations), even if the
     * text itself is unchanged. Resolve placeholders inside text leaves instead.
     *
     * Physical container title templates deliberately originate as legacy strings and can be
     * deserialized safely because there is no existing rich title to preserve.
     */
    private boolean applyInitialTitleOverride(InventoryOpenEvent event, String configuredContainerTitle,
                                              String resolvedLegacyTitle) {
        PaperTitleOverrideSupport paper = paperTitleSupport;
        if (paper == null) return false;
        try {
            if (configuredContainerTitle != null) {
                Object component = paper.legacyDeserializeMethod.invoke(paper.serializer, resolvedLegacyTitle);
                paper.titleOverrideMethod.invoke(event, component);
                return true;
            }

            // A prior listener's title override has precedence over the view's original title.
            Object original = paper.currentOverrideMethod == null ? null
                    : paper.currentOverrideMethod.invoke(event);
            if (original == null && paper.viewTitleMethod != null) {
                original = paper.viewTitleMethod.invoke(event.getView());
            }
            if (original == null) return false;

            Object resolved = resolveComponentTitle(original, paper, this::resolveTitle);
            if (resolved == original) {
                // A placeholder split across components is not yet supported by the rich path;
                // retain the legacy fallback rather than leaving unresolved aliases on-screen.
                return false;
            }
            paper.titleOverrideMethod.invoke(event, resolved);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException exception) {
            warnFailureOnce(exception);
            return false;
        }
    }

    /**
     * Rewrites text nodes without flattening Adventure components. Existing parents, fonts,
     * decorations, event metadata and sibling components are retained. Legacy colour codes
     * emitted by the VoxelCore glyph resolver are interpreted only in the replacement leaf.
     * Its style is inherited from the original text node unless a colour code overrides it.
     */
    private static Object resolveComponentTitle(Object component, PaperTitleOverrideSupport paper,
                                                Function<String, String> resolver)
            throws InvocationTargetException, IllegalAccessException {
        List<?> existingChildren = (List<?>) paper.childrenMethod.invoke(component);
        List<Object> newChildren = new ArrayList<Object>(existingChildren.size() + 1);
        Object rewritten = component;
        boolean changed = false;

        if (paper.textComponentClass.isInstance(component)) {
            String original = (String) paper.contentMethod.invoke(component);
            String resolved = resolver.apply(original);
            if (resolved != null && !resolved.equals(original)) {
                rewritten = paper.withContentMethod.invoke(component, "");
                newChildren.add(paper.legacyDeserializeMethod.invoke(paper.serializer, resolved));
                changed = true;
            }
        }

        for (Object child : existingChildren) {
            Object replacement = resolveComponentTitle(child, paper, resolver);
            newChildren.add(replacement);
            if (replacement != child) changed = true;
        }

        if (!changed) return component;
        return paper.withChildrenMethod.invoke(rewritten, newChildren);
    }

    private static boolean sameInventory(Inventory current, Inventory opened) {
        return current == opened || (current != null && current.equals(opened));
    }

    private static PaperTitleOverrideSupport findPaperTitleOverrideSupport() {
        try {
            Class<?> componentType = Class.forName("net.kyori.adventure.text.Component");
            Class<?> textComponentType = Class.forName("net.kyori.adventure.text.TextComponent");
            Method override = InventoryOpenEvent.class.getMethod("titleOverride", componentType);
            Method currentOverride = InventoryOpenEvent.class.getMethod("titleOverride");
            Method viewTitle = InventoryView.class.getMethod("title");
            Method children = componentType.getMethod("children");
            Method withChildren = componentType.getMethod("children", List.class);
            Method content = textComponentType.getMethod("content");
            Method withContent = textComponentType.getMethod("content", String.class);
            Class<?> serializerType = Class.forName(
                    "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            Object serializer = serializerType.getMethod("legacySection").invoke(null);
            Method deserialize = serializerType.getMethod("deserialize", String.class);
            return new PaperTitleOverrideSupport(override, currentOverride, viewTitle,
                    textComponentType, children, withChildren, content, withContent,
                    serializer, deserialize);
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
        private final Method currentOverrideMethod;
        private final Method viewTitleMethod;
        private final Class<?> textComponentClass;
        private final Method childrenMethod;
        private final Method withChildrenMethod;
        private final Method contentMethod;
        private final Method withContentMethod;
        private final Object serializer;
        private final Method legacyDeserializeMethod;

        private PaperTitleOverrideSupport(Method titleOverrideMethod, Method currentOverrideMethod,
                                          Method viewTitleMethod, Class<?> textComponentClass,
                                          Method childrenMethod, Method withChildrenMethod,
                                          Method contentMethod, Method withContentMethod,
                                          Object serializer, Method legacyDeserializeMethod) {
            this.titleOverrideMethod = titleOverrideMethod;
            this.currentOverrideMethod = currentOverrideMethod;
            this.viewTitleMethod = viewTitleMethod;
            this.textComponentClass = textComponentClass;
            this.childrenMethod = childrenMethod;
            this.withChildrenMethod = withChildrenMethod;
            this.contentMethod = contentMethod;
            this.withContentMethod = withContentMethod;
            this.serializer = serializer;
            this.legacyDeserializeMethod = legacyDeserializeMethod;
        }
    }

    private void warnFailureOnce(Exception exception) {
        if (warnedFailure) return;
        warnedFailure = true;
        plugin.getLogger().warning("Unable to apply resolved inventory title placeholders: " + exception.getMessage());
    }
}
