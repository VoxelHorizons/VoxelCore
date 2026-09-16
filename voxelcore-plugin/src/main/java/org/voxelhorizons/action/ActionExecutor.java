package org.voxelhorizons.action;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.voxelhorizons.block.BlockManager;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.action.ActionDefinition;
import org.voxelhorizons.content.action.ActionType;
import org.voxelhorizons.item.ItemManager;

import java.util.List;

/** Executes the small, reusable built-in action vocabulary on the server thread. */
public final class ActionExecutor {
    private final BlockManager blocks;
    private final ItemManager items;

    public ActionExecutor(BlockManager blocks, ItemManager items) {
        this.blocks = blocks;
        this.items = items;
    }

    public boolean execute(List<ActionDefinition> actions, ActionContext context) {
        boolean successful = true;
        for (ActionDefinition action : actions) {
            if (!execute(action, context)) successful = false;
        }
        return successful;
    }

    private boolean execute(ActionDefinition action, ActionContext context) {
        switch (action.type()) {
            case SET_BLOCK: return setBlock(action, context);
            case REMOVE_BLOCK: return removeBlock(action, context);
            case COMMAND: return command(action, context);
            case GIVE_ITEM: return giveItem(action, context, false);
            case DROP_ITEM: return giveItem(action, context, true);
            case MESSAGE:
                context.player().sendMessage(ChatColor.translateAlternateColorCodes('&', expand(action.string("text"), context)));
                return true;
            case CANCEL:
                if (context.event() != null) context.event().setCancelled(true);
                return true;
            default: return false;
        }
    }

    private boolean setBlock(ActionDefinition action, ActionContext context) {
        Block target = target(action.string("target"), context);
        if (target == null) return false;
        String replace = action.string("replace");
        if ((replace == null || "air_only".equalsIgnoreCase(replace)) && target.getType() != Material.AIR) return false;
        ContentID id = ContentID.parse(action.string("block"), context.itemId() == null ? "voxelhorizons" : context.itemId().namespace());
        BlockState replaced = target.getState();
        if (!blocks.place(target, id, false)) return false;
        Block against = context.block() == null ? target : context.block();
        BlockPlaceEvent place = new BlockPlaceEvent(target, replaced, against, context.item(), context.player(), true,
                EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(place);
        if (place.isCancelled() || !place.canBuild()) {
            replaced.update(true, false);
            return false;
        }
        int consume = action.integer("consume", 0);
        if (consume > 0 && context.item() != null && context.player().getGameMode() != org.bukkit.GameMode.CREATIVE) {
            context.item().setAmount(Math.max(0, context.item().getAmount() - consume));
        }
        return true;
    }

    private boolean removeBlock(ActionDefinition action, ActionContext context) {
        Block target = target(action.string("target"), context);
        if (target == null || !blocks.identify(target).isPresent()) return false;
        target.setType(Material.AIR);
        return true;
    }

    private boolean command(ActionDefinition action, ActionContext context) {
        String command = expand(action.string("command"), context);
        if (command.startsWith("/")) command = command.substring(1);
        String executor = action.string("executor");
        return "console".equalsIgnoreCase(executor)
                ? Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                : Bukkit.dispatchCommand(context.player(), command);
    }

    private boolean giveItem(ActionDefinition action, ActionContext context, boolean drop) {
        ContentID id = ContentID.parse(action.string("item"), context.itemId() == null ? "voxelhorizons" : context.itemId().namespace());
        ItemStack stack;
        try { stack = items.createItem(id, action.integer("amount", 1)); }
        catch (IllegalArgumentException exception) { return false; }
        if (drop) context.player().getWorld().dropItemNaturally(context.player().getLocation(), stack);
        else context.player().getInventory().addItem(stack);
        return true;
    }

    private static Block target(String value, ActionContext context) {
        String target = value == null ? "relative" : value.toLowerCase(java.util.Locale.ROOT);
        if ("clicked".equals(target) || "self".equals(target)) return context.block();
        if ("relative".equals(target)) {
            return context.block() == null || context.face() == null ? null : context.block().getRelative(context.face());
        }
        if ("target".equals(target)) return context.player().getTargetBlock(null, 6);
        return null;
    }

    private static String expand(String value, ActionContext context) {
        if (value == null) return "";
        Block block = context.block();
        return value.replace("{player}", context.player().getName())
                .replace("{player_uuid}", context.player().getUniqueId().toString())
                .replace("{world}", context.player().getWorld().getName())
                .replace("{x}", block == null ? "" : String.valueOf(block.getX()))
                .replace("{y}", block == null ? "" : String.valueOf(block.getY()))
                .replace("{z}", block == null ? "" : String.valueOf(block.getZ()))
                .replace("{item_id}", context.itemId() == null ? "" : context.itemId().toString())
                .replace("{block_id}", context.blockId() == null ? "" : context.blockId().toString());
    }
}
