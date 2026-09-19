package org.voxelhorizons.command.ui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.block.BlockDefinition;
import org.voxelhorizons.content.item.ItemDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ContentBrowser implements Listener {
    static final int INVENTORY_SIZE = 54;
    static final int CONTENT_SLOTS = 45;
    static final int PREVIOUS_SLOT = 45;
    static final int CLOSE_SLOT = 49;
    static final int NEXT_SLOT = 53;

    public enum Type {
        ITEMS("Items"),
        BLOCKS("Blocks");

        private final String title;

        Type(String title) {
            this.title = title;
        }

        String title() {
            return title;
        }
    }

    private final VoxelCore plugin;

    public ContentBrowser(VoxelCore plugin) {
        this.plugin = plugin;
    }

    public void openItems(Player player) {
        open(player, Type.ITEMS, 0);
    }

    public void openBlocks(Player player) {
        open(player, Type.BLOCKS, 0);
    }

    private void open(Player player, Type type, int requestedPage) {
        List<ContentID> ids = ids(type);
        int pages = pageCount(ids.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        long revision = plugin.getContentRuntime().current().revision();

        BrowserHolder holder = new BrowserHolder(type, ids, page, revision);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                "VC " + type.title() + " " + (page + 1) + "/" + pages);
        holder.inventory = inventory;

        int start = page * CONTENT_SLOTS;
        int end = Math.min(ids.size(), start + CONTENT_SLOTS);
        for (int index = start; index < end; index++) {
            ContentID id = ids.get(index);
            ItemStack preview = preview(id);
            inventory.setItem(index - start, preview);
        }

        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, button(Material.ARROW,
                    ChatColor.YELLOW + "Previous Page",
                    ChatColor.GRAY + "Page " + page + "/" + pages));
        }

        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER,
                ChatColor.RED + "Close",
                ChatColor.GRAY + type.title() + ": " + ids.size(),
                ChatColor.GRAY + "Page " + (page + 1) + "/" + pages));

        if (page + 1 < pages) {
            inventory.setItem(NEXT_SLOT, button(Material.ARROW,
                    ChatColor.YELLOW + "Next Page",
                    ChatColor.GRAY + "Page " + (page + 2) + "/" + pages));
        }

        player.openInventory(inventory);
    }

    private ItemStack preview(ContentID id) {
        ItemStack stack = plugin.getItemManager().createItem(id, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
        if (!lore.isEmpty()) lore.add("");
        lore.add(ChatColor.DARK_GRAY + id.toString());
        lore.add(ChatColor.GRAY + "Click: take one");
        lore.add(ChatColor.GRAY + "Shift-click: take a stack");
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack button(Material material, String name, String... loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<String>();
        Collections.addAll(lore, loreLines);
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private List<ContentID> ids(Type type) {
        List<ContentID> ids = new ArrayList<ContentID>();

        if (type == Type.ITEMS) {
            for (ItemDefinition definition : plugin.getItemRegistry().entries().values()) {
                if (!definition.abstractDefinition()) ids.add(definition.id());
            }
        } else {
            for (BlockDefinition definition : plugin.getBlockRegistry().entries().values()) {
                if (definition.abstractDefinition()) continue;
                ItemDefinition item = plugin.getItemRegistry().get(definition.id()).orElse(null);
                if (item != null && !item.abstractDefinition()) ids.add(definition.id());
            }
        }

        Collections.sort(ids, new Comparator<ContentID>() {
            @Override public int compare(ContentID left, ContentID right) {
                return left.toString().compareTo(right.toString());
            }
        });
        return ids;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof BrowserHolder)) return;

        event.setCancelled(true);
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player)) return;

        Player player = (Player) clicker;
        BrowserHolder holder = (BrowserHolder) top.getHolder();
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= INVENTORY_SIZE) return;

        if (holder.revision != plugin.getContentRuntime().current().revision()) {
            player.sendMessage(ChatColor.YELLOW + "VoxelCore content changed; refreshing browser.");
            open(player, holder.type, holder.page);
            return;
        }

        if (rawSlot == PREVIOUS_SLOT && holder.page > 0) {
            open(player, holder.type, holder.page - 1);
            return;
        }
        if (rawSlot == NEXT_SLOT && (holder.page + 1) * CONTENT_SLOTS < holder.ids.size()) {
            open(player, holder.type, holder.page + 1);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        if (rawSlot >= CONTENT_SLOTS) return;
        int index = holder.page * CONTENT_SLOTS + rawSlot;
        if (index < 0 || index >= holder.ids.size()) return;

        ContentID id = holder.ids.get(index);
        try {
            ItemStack one = plugin.getItemManager().createItem(id, 1);
            int amount = event.isShiftClick() ? Math.max(1, one.getMaxStackSize()) : 1;
            ItemStack given = plugin.getItemManager().createItem(id, amount);
            Map<Integer, ItemStack> remaining = player.getInventory().addItem(given);
            if (!remaining.isEmpty()) {
                for (ItemStack overflow : remaining.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), overflow);
                }
                player.sendMessage(ChatColor.YELLOW + "Inventory full; overflow dropped at your feet.");
            }
        } catch (IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BrowserHolder) {
            event.setCancelled(true);
        }
    }

    static int pageCount(int entries) {
        return Math.max(1, (entries + CONTENT_SLOTS - 1) / CONTENT_SLOTS);
    }

    private static final class BrowserHolder implements InventoryHolder {
        private final Type type;
        private final List<ContentID> ids;
        private final int page;
        private final long revision;
        private Inventory inventory;

        private BrowserHolder(Type type, List<ContentID> ids, int page, long revision) {
            this.type = type;
            this.ids = Collections.unmodifiableList(new ArrayList<ContentID>(ids));
            this.page = page;
            this.revision = revision;
        }

        @Override public Inventory getInventory() {
            return inventory;
        }
    }
}
