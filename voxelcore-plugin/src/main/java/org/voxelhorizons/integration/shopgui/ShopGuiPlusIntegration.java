package org.voxelhorizons.integration.shopgui;

import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.event.ShopGUIPlusPostEnableEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.item.ItemManager;

public final class ShopGuiPlusIntegration implements Listener {

    private final VoxelCore plugin;
    private final ItemManager itemManager;
    private boolean registered;

    private ShopGuiPlusIntegration(VoxelCore plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    public static void register(VoxelCore plugin, ItemManager itemManager) {
        plugin.getServer().getPluginManager().registerEvents(
                new ShopGuiPlusIntegration(plugin, itemManager), plugin);
        plugin.getLogger().info("ShopGUI+ detected; waiting to register the VoxelCore item provider.");
    }

    @EventHandler
    public void onShopGuiPlusPostEnable(ShopGUIPlusPostEnableEvent event) {
        if (registered) return;

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
        plugin.getLogger().info("Registered the VoxelCore custom-item provider with ShopGUI+.");
    }
}
