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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
    // Unavailable optional UI models use a vanilla icon rather than disabling VoxelCore.
    private volatile String activeColorMaterial = "FIREWORK_STAR";
    private volatile String activeSelectedMaterial = "";

    public ColorPickerMenu(VoxelCore plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
        this.file = new File(plugin.getDataFolder(), "colorpicker.yml");
        if (!file.exists()) plugin.saveResource("colorpicker.yml", false);
        reload();
    }

    public void reload() {
        ColorPickerSettings next = ColorPickerSettings.from(YamlConfiguration.loadConfiguration(file));
        // Content pack items are optional; an unavailable UI icon must never
        // take the entire server plugin offline.
        String normal = availableButton(next.colorMaterial, "buttons.color-material", "FIREWORK_STAR");
        String selected = next.selectedShadeMaterial.isEmpty() ? ""
                : availableButton(next.selectedShadeMaterial, "buttons.selected-shade-material", "");
        activeColorMaterial = normal;
        activeSelectedMaterial = selected;
        settings = next;
    }

    private String availableButton(String identifier, String configKey, String fallback) {
        try {
            if (isCustomButton(identifier)) {
                ItemStack stack = createButtonItem(identifier);
                if (!items.isDyeable(stack))
                    plugin.getLogger().warning("Color Picker " + configKey + " '" + identifier
                            + "' is not dyeable; RGB swatches require a tintable item model.");
            } else {
                resolveMaterial(identifier);
            }
            return identifier;
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Color Picker " + configKey + " '" + identifier
                    + "' could not be loaded (" + exception.getMessage() + "). "
                    + (fallback.isEmpty() ? "Using the normal slot icon." :
                       "Falling back to " + fallback + ".")
                    + " Check plugins/VoxelCore/content/<namespace>/content/ for the item definition.");
            return fallback;
        }
    }

    private ItemStack createButtonItem(String identifier) {
        IllegalArgumentException original = null;
        for (ContentID candidate : buttonContentCandidates(identifier)) {
            try {
                return items.createRenderItem(candidate);
            } catch (IllegalArgumentException unavailable) {
                if (original == null) original = unavailable;
            }
        }
        throw new IllegalArgumentException("Unknown item for '" + identifier
                + "'; tried " + buttonContentCandidates(identifier), original);
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
        String title = title(session);
        Inventory top = Bukkit.createInventory(session, config.size, title);
        session.inventory = top;
        paint(session);
        player.openInventory(top);
        return true;
    }

    private String title(Session session) {
        ColorPickerSettings config = session.config;
        String raw = config.title.replace("{palette}", session.palette().id)
                .replace("{name}", session.palette().name);
        // Always resolve the same UI glyphs and offsets. Geyser/VoxelCore handle
        // the Bedrock translation downstream; do not replace them with a plain title.
        String resolved = plugin.getTextPlaceholderService().resolve(raw, true, true);
        // Only fall back if a required authored glyph is missing, irrespective of client.
        if (resolved.contains(":colorpicker_"))
            resolved = "&8Color Picker - " + session.palette().name;
        return ChatColor.translateAlternateColorCodes('&', resolved);
    }

    private void paint(Session session) {
        ColorPickerSettings config = session.config;
        ColorPickerSettings.Palette selected = session.palette();
        for (int i = 0; i < config.palettes.size(); i++) {
            ColorPickerSettings.Palette p = config.palettes.get(i);
            int rgb = p.buttonColor;
            String name = substitute(config.colorName, p, rgb);
            boolean selectedPalette = i == session.paletteIndex;
            if (selectedPalette && !name.isEmpty())
                name = config.selectedPrefix + name;
            // Selected primary palette uses the same 18x18 selection model as
            // the active shade, with the representative palette RGB tint.
            String iconId = selectedPalette && !activeSelectedMaterial.isEmpty()
                    ? activeSelectedMaterial : activeColorMaterial;
            session.inventory.setItem(p.slot, icon(iconId, rgb,
                    name, config.colorLore, p));
        }
        for (int i = 0; i < config.shadeSlots.size(); i++) {
            ItemStack swatch = null;
            if (i < selected.shades.size()) {
                int rgb = selected.shades.get(i).intValue();
                boolean selectedShade = session.shadeIndex == i;
                // The 18x18 transparent-border model replaces ONLY the selected shade
                // icon. Its hollow centre leaves the colour painted in the GUI visible.
                String iconId = selectedShade && !activeSelectedMaterial.isEmpty()
                        ? activeSelectedMaterial : activeColorMaterial;
                String shadeName = substitute(config.shadeName, selected, rgb);
                if (selectedShade && !shadeName.isEmpty())
                    shadeName = config.selectedPrefix + shadeName;
                swatch = icon(iconId, rgb, shadeName, config.shadeLore, selected);
            }
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

    /**
     * The sample pack declares voxel:ui-colorpicker_slot for the item model
     * voxel:ui/colorpicker/slot. Nested paths need the final underscore:
     * voxel:ui/colorpicker/slot -> voxel:ui-colorpicker_slot.
     * One-level paths retain their historic hyphen mapping.
     */
    static ContentID parseButtonContentId(String configured) {
        String raw = configured.trim();
        String normalized = raw.replace('/', '-');
        if (raw.indexOf('/') >= 0 && raw.indexOf('/') != raw.lastIndexOf('/')) {
            int last = normalized.lastIndexOf('-');
            normalized = normalized.substring(0, last) + "_" + normalized.substring(last + 1);
        }
        return ContentID.parse(normalized, "voxel");
    }

    /** Also accept the old all-hyphen aliases if a pack has already used them. */
    static List<ContentID> buttonContentCandidates(String configured) {
        List<ContentID> ids = new ArrayList<ContentID>();
        ContentID preferred = parseButtonContentId(configured);
        ids.add(preferred);
        ContentID old = ContentID.parse(configured.trim().replace('/', '-'), "voxel");
        if (!old.equals(preferred)) ids.add(old);
        return ids;
    }

    private static boolean isCustomButton(String configured) {
        return configured != null && configured.indexOf(':') >= 0
                && !configured.toLowerCase(Locale.ROOT).startsWith("minecraft:");
    }

    private static Material resolveMaterial(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null && "FIREWORK_STAR".equalsIgnoreCase(name))
            material = Material.matchMaterial("FIREWORK_CHARGE");
        if (material == null) throw new IllegalArgumentException("Unknown material: " + name);
        return material;
    }

    static List<String> formatLore(List<String> templates, ColorPickerSettings.Palette palette,
                                   int rgb) {
        List<String> result = new ArrayList<String>();
        for (String template : templates) {
            String line = substitute(template, palette, rgb);
            if (!line.isEmpty())
                result.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return result;
    }

    private ItemStack icon(String materialName, int rgb, String name, List<String> loreTemplates,
                           ColorPickerSettings.Palette palette) {
        ItemStack stack = isCustomButton(materialName)
                ? createButtonItem(materialName)
                : new ItemStack(resolveMaterial(materialName));
        // Each model references one original PNG. Its element faces apply
        // tintindex 0 only to the inner square, white outline and bottom shade;
        // the selected outer grey/black border remains untinted.
        if (isCustomButton(materialName) && items.isDyeable(stack))
            stack = items.setDyeColor(stack, rgb);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (rgb >= 0 && meta instanceof FireworkEffectMeta) {
                ((FireworkEffectMeta) meta).setEffect(
                        FireworkEffect.builder().withColor(Color.fromRGB(rgb)).build());
            }
            // An empty name must not resurrect the underlying PAPER/FIREWORK_STAR
            // or an inherited custom icon's display name. A blank custom name
            // keeps the icon visually unnamed instead of using the vanilla default.
            meta.setDisplayName(name.isEmpty() ? " "
                    : ChatColor.translateAlternateColorCodes('&', name));
            // Always overwrite authored item lore, including when [] means none.
            // No automatic hexadecimal lore is appended here.
            meta.setLore(formatLore(loreTemplates, palette, rgb));
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
            // Only output collection commits the tint; the displayed item is a
            // virtual preview and must never be transferred by vanilla logic.
            boolean shift = shiftOutputClick(event.getClick());
            if (!shift && !ordinaryClick(event.getClick())) return;
            ItemStack source = top.getItem(config.inputSlot);
            ItemStack result = preview(source, session);
            if (result == null) {
                send(player, config, "no-input", "&cPlace a dyeable item in the input slot.");
                return;
            }
            if (shift) {
                // One result (amount 1) may merge with an existing stack even
                // when no empty slot exists. Try the inventory transfer BEFORE
                // consuming input so a full inventory does not lose the item.
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(result.clone());
                if (!overflow.isEmpty()) {
                    send(player, config, "inventory-full", "&cMake room in your inventory first.");
                    return;
                }
            } else {
                if (!isEmpty(event.getCursor())) {
                    send(player, config, "clear-cursor", "&cEmpty your cursor before collecting the result.");
                    return;
                }
            }
            top.setItem(config.inputSlot, null);
            top.setItem(config.outputSlot, null);
            if (!shift) event.setCursor(result);
            paint(session);
            send(player, config, "painted", "&aItem recoloured to {hex}.", session.color());
            return;
        }
        for (int i = 0; i < config.palettes.size(); i++) {
            if (raw == config.palettes.get(i).slot) {
                if (session.paletteIndex != i) {
                    session.paletteIndex = i;
                    session.shadeIndex = 0;
                    // The background is shared across palettes, so repaint
                    // in place: no inventory reopen, input transfer or flicker.
                    paint(session);
                }
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

    /** Only allow the two intentional quick-move clicks, not drop/creative swaps. */
    static boolean shiftOutputClick(ClickType click) {
        return click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
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
