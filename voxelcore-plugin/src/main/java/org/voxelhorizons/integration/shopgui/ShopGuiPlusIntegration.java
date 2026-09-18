package org.voxelhorizons.integration.shopgui;

import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.event.ShopGUIPlusPostEnableEvent;
import net.brcdev.shopgui.event.ShopsPostLoadEvent;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.item.ItemManager;
import org.voxelhorizons.text.TextPlaceholderService;

public final class ShopGuiPlusIntegration implements Listener {

    private final VoxelCore plugin;
    private final ItemManager itemManager;
    private final TextPlaceholderService placeholders;
    private boolean registered;

    private ShopGuiPlusIntegration(VoxelCore plugin, ItemManager itemManager,
                                   TextPlaceholderService placeholders) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.placeholders = placeholders;
    }

    public static void register(VoxelCore plugin, ItemManager itemManager,
                                TextPlaceholderService placeholders) {
        plugin.getServer().getPluginManager().registerEvents(
                new ShopGuiPlusIntegration(plugin, itemManager, placeholders), plugin);
        plugin.getLogger().info("ShopGUI+ detected; waiting to register the VoxelCore item provider.");
    }

    @EventHandler
    public void onShopGuiPlusPostEnable(ShopGUIPlusPostEnableEvent event) {
        if (registered) return;

        int resolved = ShopGuiConfigurationPlaceholderProcessor.process(
                ShopGuiPlusApi.getPlugin(), placeholders);

        VoxelCoreItemResolver resolver = new VoxelCoreItemResolver() {
            @Override public org.bukkit.inventory.ItemStack create(ContentID id) {
                return itemManager.createItem(id);
            }

            @Override public java.util.Optional<ContentID> identify(org.bukkit.inventory.ItemStack stack) {
                return itemManager.identify(stack);
            }
        };
        ShopGuiPlusApi.registerItemProvider(new VoxelCoreItemProvider(resolver, plugin.getLogger()));
        registered = true;
        plugin.getLogger().info("Registered the VoxelCore custom-item provider with ShopGUI+ and resolved "
                + resolved + " configuration placeholder value(s).");
    }

    @EventHandler
    public void onShopsPostLoad(ShopsPostLoadEvent event) {
        int resolved = ShopGuiConfigurationPlaceholderProcessor.process(
                ShopGuiPlusApi.getPlugin(), placeholders);
        resolved += ShopGuiLoadedContentPlaceholderProcessor.process(
                ShopGuiPlusApi.getPlugin(), placeholders);
        if (resolved > 0) {
            plugin.getLogger().info("Resolved " + resolved
                    + " VoxelCore placeholder value(s) after the ShopGUI+ reload.");
        }
    }

    /**
     * ShopGUI+ builds player-specific price and action lore while opening the inventory, after its
     * stored ShopItem has been loaded. ShopGUI+ can replace those stacks during the first several
     * ticks of its open animation/session setup, so resolve the live top inventory repeatedly for a
     * short bounded window. Only text containing a registered VoxelCore placeholder is changed.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopInventoryOpen(InventoryOpenEvent event) {
        scheduleInventoryResolution(event.getPlayer(), event.getInventory(), 10);
    }

    /**
     * Amount-selection controls rebuild their preview stack after the inventory has already opened.
     * ShopGUI+ also cancels its own control clicks, so observe cancelled events at MONITOR and begin
     * resolving on the following tick, after ShopGUI+ has installed the replacement stack.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onShopInventoryClick(InventoryClickEvent event) {
        scheduleInventoryResolution(event.getWhoClicked(), event.getView().getTopInventory(), 5);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onShopInventoryDrag(InventoryDragEvent event) {
        scheduleInventoryResolution(event.getWhoClicked(), event.getView().getTopInventory(), 5);
    }

    private void scheduleInventoryResolution(final HumanEntity viewer, final Inventory opened,
                                             final int passes) {
        if (opened == null) return;
        new BukkitRunnable() {
            private int remainingPasses = passes;

            @Override public void run() {
                InventoryView view = viewer.getOpenInventory();
                if (view == null || !sameInventory(view.getTopInventory(), opened)) {
                    cancel();
                    return;
                }

                for (int slot = 0; slot < opened.getSize(); slot++) {
                    ItemStack stack = opened.getItem(slot);
                    if (ShopGuiLoadedContentPlaceholderProcessor.process(stack, placeholders) > 0) {
                        opened.setItem(slot, stack);
                    }
                }

                remainingPasses--;
                if (remainingPasses <= 0) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static boolean sameInventory(Inventory first, Inventory second) {
        return first == second || (first != null && first.equals(second));
    }
}
