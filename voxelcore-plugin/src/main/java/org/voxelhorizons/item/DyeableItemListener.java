package org.voxelhorizons.item;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;

/** Allows a dye on the cursor to recolour a dyeable VoxelCore item in an inventory. */
public final class DyeableItemListener implements Listener {
    private final ItemManager items;
    public DyeableItemListener(ItemManager items) { this.items = items; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        ItemStack target = event.getCurrentItem();
        ItemStack dye = event.getCursor();
        Integer rgb = dyeColor(dye);
        if (rgb == null || !items.isDyeable(target)) return;
        event.setCancelled(true);
        event.setCurrentItem(items.setDyeColor(target.clone(), rgb.intValue()));
        boolean creative = event.getWhoClicked() instanceof Player
                && ((Player) event.getWhoClicked()).getGameMode() == GameMode.CREATIVE;
        if (!creative) {
            if (dye.getAmount() <= 1) event.setCursor(null);
            else { ItemStack remainder = dye.clone(); remainder.setAmount(dye.getAmount() - 1); event.setCursor(remainder); }
        }
    }

    @SuppressWarnings("deprecation")
    public static Integer dyeColor(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return null;
        String name = stack.getType().name();
        DyeColor dye = null;
        if (name.endsWith("_DYE")) {
            try { dye = DyeColor.valueOf(name.substring(0, name.length() - 4)); }
            catch (IllegalArgumentException ignored) { return null; }
        } else if ("INK_SACK".equals(name)) {
            dye = DyeColor.getByDyeData((byte) stack.getDurability());
        }
        if (dye == null) return null;
        Color color = dye.getColor();
        return Integer.valueOf(color.asRGB());
    }
}
