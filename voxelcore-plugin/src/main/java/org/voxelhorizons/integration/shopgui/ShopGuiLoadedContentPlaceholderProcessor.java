package org.voxelhorizons.integration.shopgui;

import net.brcdev.shopgui.ShopGuiPlugin;
import net.brcdev.shopgui.exception.shop.ShopsNotLoadedException;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.voxelhorizons.text.TextPlaceholderService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves text that ShopGUI+ has already compiled from individual shop files. */
final class ShopGuiLoadedContentPlaceholderProcessor {

    private ShopGuiLoadedContentPlaceholderProcessor() {
    }

    static int process(ShopGuiPlugin shopGui, TextPlaceholderService placeholders) {
        if (shopGui == null || placeholders == null || shopGui.getShopManager() == null) return 0;

        int changed = 0;
        try {
            for (Shop shop : shopGui.getShopManager().getShops()) {
                changed += process(shop, placeholders);
            }
        } catch (ShopsNotLoadedException ignored) {
            return 0;
        } catch (LinkageError ignored) {
            // Keep this optional integration safe if a future ShopGUI+ release changes its API.
            return 0;
        }
        return changed;
    }

    private static int process(Shop shop, TextPlaceholderService placeholders) {
        if (shop == null) return 0;
        int changed = 0;

        String name = shop.getName();
        String resolvedName = placeholders.resolve(name);
        if (!same(name, resolvedName)) {
            shop.setName(resolvedName);
            changed++;
        }

        Map<Integer, String> names = shop.getNamePerPage();
        if (names != null) {
            Map<Integer, String> resolvedNames = new LinkedHashMap<Integer, String>();
            boolean namesChanged = false;
            for (Map.Entry<Integer, String> entry : names.entrySet()) {
                String resolved = placeholders.resolve(entry.getValue());
                resolvedNames.put(entry.getKey(), resolved);
                namesChanged |= !same(entry.getValue(), resolved);
            }
            if (namesChanged) {
                shop.setNamePerPage(resolvedNames);
                changed++;
            }
        }

        changed += process(shop.getFillItem(), placeholders);
        List<ShopItem> items = shop.getShopItems();
        if (items != null) {
            for (ShopItem item : items) {
                if (item == null) continue;
                changed += process(item.getItem(), placeholders);
                changed += process(item.getPlaceholder(), placeholders);
            }
        }
        return changed;
    }

    static int process(ItemStack stack, TextPlaceholderService placeholders) {
        if (stack == null || placeholders == null || !stack.hasItemMeta()) return 0;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0;

        boolean changed = false;
        if (meta.hasDisplayName()) {
            String current = meta.getDisplayName();
            String resolved = placeholders.resolve(current);
            if (!same(current, resolved)) {
                meta.setDisplayName(resolved);
                changed = true;
            }
        }
        if (meta.hasLore()) {
            List<String> current = meta.getLore();
            if (current != null) {
                List<String> resolved = new ArrayList<String>(current.size());
                boolean loreChanged = false;
                for (String line : current) {
                    String resolvedLine = placeholders.resolve(line);
                    resolved.add(resolvedLine);
                    loreChanged |= !same(line, resolvedLine);
                }
                if (loreChanged) {
                    meta.setLore(resolved);
                    changed = true;
                }
            }
        }
        if (!changed) return 0;
        stack.setItemMeta(meta);
        return 1;
    }

    private static boolean same(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }
}
