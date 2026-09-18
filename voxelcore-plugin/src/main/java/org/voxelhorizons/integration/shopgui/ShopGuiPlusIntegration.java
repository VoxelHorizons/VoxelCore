package org.voxelhorizons.integration.shopgui;

import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.event.ShopGUIPlusPostEnableEvent;
import net.brcdev.shopgui.event.ShopsPostLoadEvent;
import net.brcdev.shopgui.exception.player.PlayerDataNotLoadedException;
import net.brcdev.shopgui.gui.gui.OpenGui;
import net.brcdev.shopgui.player.PlayerData;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
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
     * stored ShopItem has been loaded. Resolve the final rendered stacks on the following tick,
     * scoped to inventories tracked as ShopGUI+ sessions.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopInventoryOpen(InventoryOpenEvent event) {
        final HumanEntity viewer = event.getPlayer();
        final Inventory opened = event.getInventory();
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                InventoryView view = viewer.getOpenInventory();
                if (view == null || !sameInventory(view.getTopInventory(), opened)) return;
                if (!isShopGuiInventory(viewer, opened)) return;

                for (int slot = 0; slot < opened.getSize(); slot++) {
                    ItemStack stack = opened.getItem(slot);
                    if (ShopGuiLoadedContentPlaceholderProcessor.process(stack, placeholders) > 0) {
                        opened.setItem(slot, stack);
                    }
                }
            }
        });
    }

    private static boolean isShopGuiInventory(HumanEntity viewer, Inventory inventory) {
        if (!(viewer instanceof org.bukkit.entity.Player)) return false;
        try {
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) viewer;
            if (ShopGuiPlusApi.getPlugin().getPlayerManager() == null
                    || !ShopGuiPlusApi.getPlugin().getPlayerManager().isPlayerLoaded(player)) return false;
            PlayerData data = ShopGuiPlusApi.getPlugin().getPlayerManager().getPlayerData(player);
            if (data == null || !data.hasOpenGui()) return false;
            OpenGui gui = data.getOpenGui();
            if (gui == null) return false;
            return sameInventory(inventory, gui.getOpenInventory())
                    || sameInventory(inventory, gui.getInventory());
        } catch (PlayerDataNotLoadedException ignored) {
            return false;
        } catch (LinkageError ignored) {
            return false;
        }
    }

    private static boolean sameInventory(Inventory first, Inventory second) {
        return first == second || (first != null && first.equals(second));
    }
}
