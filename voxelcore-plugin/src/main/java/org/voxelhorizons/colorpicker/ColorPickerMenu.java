package org.voxelhorizons.colorpicker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.item.ItemManager;
import org.voxelhorizons.pack.BedrockPlayers;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * Server-side six-row RGB picker. Only one actual item is held in the input; the output is
 * a protected preview, not a second owned stack. Input is returned on all normal closes.
 */
public final class ColorPickerMenu implements Listener {
    private final VoxelCore plugin;
    private final ItemManager items;
    private final File file;
    private volatile ColorPickerSettings settings;

    public ColorPickerMenu(VoxelCore plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
        this.file = new File(plugin.getDataFolder(), "colorpicker.yml");
        if (!file.exists()) plugin.saveResource("colorpicker.yml", false);
        reload();
    }

    public void reload() {
        ColorPickerSettings next = ColorPickerSettings.from(YamlConfiguration.loadConfiguration(file));
        // Resolve authored button icons once at load/reload time. This catches missing
        // custom definitions immediately, rather than silently showing a PAPER icon.
        if (isCustomButton(next.colorMaterial)) {
            try {
                items.createRenderItem(parseButtonContentId(next.colorMaterial));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid colorpicker.yml buttons.color-material '"
                        + next.colorMaterial + "': " + exception.getMessage(), exception);
            }
        }
        settings = next;
    }

    public boolean open(Player player) {
        ColorPickerSettings config = settings;
        if (!config.enabled) {
            send(player, config, "disabled", "&cThe color picker is disabled.");
            return false;
        }
        if (!plugin.getVersionAdapter().items().supportsDynamicItemColors()) {
            send(player, config, "unsupported", "&cRGB custom item dyeing requires Minecraft 1.21.4+.");
            return false;
        }
        Session session = new Session(config);
        String title = title(player, session);
        Inventory top = Bukkit.createInventory(session, config.size, title);
        session.inventory = top;
        paint(session);
        player.openInventory(top);
        return true;
    }

    private String title(Player player, Session session) {
        ColorPickerSettings config = session.config;
        String palette = session.palette().id;
        String template = BedrockPlayers.isBedrock(player.getUniqueId()) ? config.bedrockTitle : config.javaTitle;
        String raw = template.replace("{palette}", palette).replace("{name}", session.palette().name);
        String resolved = BedrockPlayers.isBedrock(player.getUniqueId()) ? raw
                : plugin.getTextPlaceholderService().resolve(raw, true, true);
        // The art lives in the author's content pack. A missing glyph must not show a raw alias.
        if (resolved.contains(":colorpicker_"))
            resolved = config.fallbackTitle.replace("{palette}", palette).replace("{name}", session.palette().name);
        return ChatColor.translateAlternateColorCodes('&', resolved);
    }

    private void paint(Session session) {
        ColorPickerSettings config = session.config;
        ColorPickerSettings.Palette selected = session.palette();
        for (int i = 0; i < config.palettes.size(); i++) {
            ColorPickerSettings.Palette p = config.palettes.get(i);
            String name = substitute(config.colorName, p, p.shades.get(0).intValue());
            if (i == session.paletteIndex) name = config.selectedPrefix + name;
            session.inventory.setItem(p.slot, icon(config.colorMaterial, p.shades.get(0).intValue(), name));
        }
        for (int i = 0; i < config.shadeSlots.size(); i++) {
            ItemStack swatch = i < selected.shades.size()
                    ? icon(config.colorMaterial, selected.shades.get(i).intValue(),
                        (session.shadeIndex == i ? config.selectedPrefix : "")
                                + substitute(config.shadeName, selected, selected.shades.get(i).intValue()))
                    : null;
            session.inventory.setItem(config.shadeSlots.get(i).intValue(), swatch);
        }
        ItemStack source = session.inventory.getItem(config.inputSlot);
        session.inventory.setItem(config.outputSlot, preview(source, session));
    }

    private ItemStack preview(ItemStack input, Session session) {
        if (!items.isDyeable(input)) return null;
        ItemStack result = input.clone();
        result.setAmount(1);
        return items.setDyeColor(result, session.color());
    }

    private static String substitute(String text, ColorPickerSettings.Palette palette, int rgb) {
        return text.replace("{palette}", palette.name).replace("{name}", palette.name)
                .replace("{hex}", ColorPickerSettings.formatHex(rgb));
    }

    /** Resource-style identifiers (voxel:ui/blank) map to content keys (voxel:ui-blank). */
    static ContentID parseButtonContentId(String configured) {
        return ContentID.parse(configured.trim().replace('/', '-'), "voxel");
    }

    private static boolean isCustomButton(String configured) {
        return configured != null && configured.indexOf(':') >= 0
                && !configured.toLowerCase(Locale.ROOT).startsWith("minecraft:");
    }

    private ItemStack icon(String materialName, int rgb, String name) {
        ItemStack stack;
        if (isCustomButton(materialName)) {
            // Render items allow abstract, display-only definitions without exposing
            // them through give commands or changing the custom model identity.
            stack = items.createRenderItem(parseButtonContentId(materialName));
        } else {
            Material material = Material.matchMaterial(materialName);
            if (material == null && "FIREWORK_STAR".equalsIgnoreCase(materialName))
                material = Material.matchMaterial("FIREWORK_CHARGE");
            if (material == null) throw new IllegalArgumentException(
                    "Unknown colorpicker.yml buttons.color-material: " + materialName);
            stack = new ItemStack(material);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (rgb >= 0 && meta instanceof FireworkEffectMeta) {
                ((FireworkEffectMeta) meta).setEffect(
                        FireworkEffect.builder().withColor(Color.fromRGB(rgb)).build());
            }
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (rgb >= 0) meta.setLore(Collections.singletonList(
                    ChatColor.GRAY + ColorPickerSettings.formatHex(rgb)));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Session)) return;
        Session session = (Session) top.getHolder();
        if (!(event.getWhoClicked() instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        Player player = (Player) event.getWhoClicked();
        ColorPickerSettings config = session.config;
        int raw = event.getRawSlot();

        if (raw < 0 || event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true); // A collect-to-cursor may otherwise collect preview items.
            return;
        }
        if (raw >= config.size) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                if (items.isDyeable(event.getCurrentItem())
                        && isEmpty(top.getItem(config.inputSlot))) {
                    ItemStack moved = event.getCurrentItem().clone();
                    moved.setAmount(1);
                    top.setItem(config.inputSlot, moved);
                    consumeOne(event.getCurrentItem(), event::setCurrentItem);
                    paint(session);
                }
            }
            return; // ordinary player-inventory interactions remain vanilla
        }

        // Every top slot is virtual except our one owned input; never let Minecraft
        // transfer previews/swatches via shift, number keys, swaps, or creative clicks.
        event.setCancelled(true);
        if (raw == config.inputSlot) {
            // Only ordinary left/right clicks can transfer the real item. Never interpret
            // shift-clicks, hotbar swaps, offhand swaps, drops or middle clicks as input.
            if (!ordinaryClick(event.getClick())) return;
            ItemStack input = top.getItem(config.inputSlot);
            ItemStack cursor = event.getCursor();
            if (isEmpty(cursor)) {
                if (!isEmpty(input)) {
                    event.setCursor(input.clone());
                    top.setItem(config.inputSlot, null);
                }
            } else if (isEmpty(input) && items.isDyeable(cursor)) {
                ItemStack moved = cursor.clone();
                moved.setAmount(1);
                top.setItem(config.inputSlot, moved);
                consumeOne(cursor, event::setCursor);
            } else {
                send(player, config, "invalid-item", "&cPlace one dyeable VoxelCore item in the input.");
            }
            paint(session);
            return;
        }
        if (raw == config.outputSlot) {
            // The output is a virtual live preview; taking it is the sole commit action.
            if (!ordinaryClick(event.getClick())) return;
            ItemStack source = top.getItem(config.inputSlot);
            ItemStack result = preview(source, session);
            if (result == null) {
                send(player, config, "no-input", "&cPlace a dyeable item in the input slot.");
                return;
            }
            if (!isEmpty(event.getCursor())) {
                send(player, config, "clear-cursor", "&cEmpty your cursor before collecting the result.");
                return;
            }
            // Consume exactly one input BEFORE handing over the painted output, preventing
            // a cloned preview and the original item from ever being taken together.
            top.setItem(config.inputSlot, null);
            top.setItem(config.outputSlot, null);
            event.setCursor(result);
            paint(session);
            send(player, config, "painted", "&aItem recoloured to {hex}.", session.color());
            return;
        }
        for (int i = 0; i < config.palettes.size(); i++) {
            if (raw == config.palettes.get(i).slot) {
                session.paletteIndex = i;
                session.shadeIndex = 0;
                paint(session);
                refreshTitle(player, session);
                return;
            }
        }
        for (int i = 0; i < config.shadeSlots.size(); i++) {
            if (raw == config.shadeSlots.get(i).intValue() && i < session.palette().shades.size()) {
                session.shadeIndex = i;
                paint(session);
                return;
            }
        }
    }

    private static boolean ordinaryClick(ClickType click) {
        return click == ClickType.LEFT || click == ClickType.RIGHT;
    }

    /** Preserve the original inventory-dye feature, but do not let it alter a picker session. */
    public static boolean isPickerInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof Session;
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
    }

    private interface PutStack { void put(ItemStack value); }

    private static void consumeOne(ItemStack stack, PutStack destination) {
        if (stack.getAmount() <= 1) destination.put(null);
        else {
            ItemStack remainder = stack.clone();
            remainder.setAmount(stack.getAmount() - 1);
            destination.put(remainder);
        }
    }

    private void refreshTitle(Player player, Session session) {
        try {
            Object view = player.getOpenInventory();
            Method method = view.getClass().getMethod("setTitle", String.class);
            method.invoke(view, title(player, session));
        } catch (ReflectiveOperationException ignored) {
            // Legacy APIs do not support a live title change; swatch contents still update.
        }
    }

    private void send(Player player, ColorPickerSettings config, String key, String fallback) {
        send(player, config, key, fallback, config.palettes.get(0).shades.get(0).intValue());
    }

    private void send(Player player, ColorPickerSettings config, String key, String fallback, int rgb) {
        String text = config.messages.containsKey(key) ? config.messages.get(key) : fallback;
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                text.replace("{hex}", ColorPickerSettings.formatHex(rgb))));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Session)) return;
        for (Integer raw : event.getRawSlots()) {
            if (raw.intValue() < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Session)) return;
        Session session = (Session) top.getHolder();
        ItemStack owned = top.getItem(session.config.inputSlot);
        top.setItem(session.config.inputSlot, null);
        top.setItem(session.config.outputSlot, null);
        if (!isEmpty(owned)) {
            Map<Integer, ItemStack> overflow = event.getPlayer().getInventory().addItem(owned);
            if (!overflow.isEmpty() && event.getPlayer() instanceof Player) {
                Player player = (Player) event.getPlayer();
                for (ItemStack stack : overflow.values())
                    player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }
    }

    private static final class Session implements InventoryHolder {
        private final ColorPickerSettings config;
        private int paletteIndex;
        private int shadeIndex;
        private Inventory inventory;

        private Session(ColorPickerSettings config) { this.config = config; }
        private ColorPickerSettings.Palette palette() { return config.palettes.get(paletteIndex); }
        private int color() { return palette().shades.get(shadeIndex).intValue(); }
        @Override public Inventory getInventory() { return inventory; }
    }
}
