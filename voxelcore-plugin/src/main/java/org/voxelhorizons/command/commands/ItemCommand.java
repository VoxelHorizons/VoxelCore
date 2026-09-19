package org.voxelhorizons.command.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.command.SubCommand;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.item.ItemDefinitionRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ItemCommand implements SubCommand {
    private final Map<String, SubCommand> children = new HashMap<String, SubCommand>();

    public ItemCommand() {
        register(new ListCommand());
        register(new InfoCommand());
        register(new GiveCommand());
        register(new IdentifyCommand());
        register(new VerifyCommand());
    }

    private void register(SubCommand command) {
        children.put(command.getName(), command);
        for (String alias : command.getAliases()) children.put(alias, command);
    }

    @Override public String getName() { return "item"; }
    @Override public List<String> getAliases() { return Collections.singletonList("items"); }
    @Override public String getPermission() { return "voxelcore.admin.item"; }
    @Override public boolean playerOnly() { return false; }
    @Override public Map<String, SubCommand> getChildren() { return children; }
    @Override public void execute(CommandSender sender, String[] args) {
        sender.sendMessage("Usage: /voxelcore admin item <list|info|give|identify|verify>");
    }

    private static ContentID parse(String value) {
        return ContentID.parse(value, "voxelhorizons");
    }

    static List<ContentID> listableItemIds(ItemDefinitionRegistry registry) {
        List<ContentID> ids = new ArrayList<ContentID>();
        for (ItemDefinition definition : registry.entries().values()) {
            if (!definition.abstractDefinition()) ids.add(definition.id());
        }
        Collections.sort(ids, new Comparator<ContentID>() {
            @Override public int compare(ContentID left, ContentID right) {
                return left.toString().compareTo(right.toString());
            }
        });
        return ids;
    }

    private static final class ListCommand implements SubCommand {
        @Override public String getName() { return "list"; }
        @Override public List<String> getAliases() { return Collections.emptyList(); }
        @Override public String getPermission() { return "voxelcore.admin.item.list"; }
        @Override public boolean playerOnly() { return false; }
        @Override public void execute(CommandSender sender, String[] args) {
            List<ContentID> ids = listableItemIds(VoxelCore.getInstance().getItemRegistry());
            sender.sendMessage("VoxelCore items (" + ids.size() + "): " + ids);
        }
    }

    private static final class InfoCommand implements SubCommand {
        @Override public String getName() { return "info"; }
        @Override public List<String> getAliases() { return Collections.emptyList(); }
        @Override public String getPermission() { return "voxelcore.admin.item.info"; }
        @Override public boolean playerOnly() { return false; }
        @Override public void execute(CommandSender sender, String[] args) {
            if (args.length < 1) {
                sender.sendMessage("Usage: /voxelcore admin item info <content-id>");
                return;
            }
            ContentID id = parse(args[0]);
            Optional<ItemDefinition> definition = VoxelCore.getInstance().getItemManager().getDefinition(id);
            if (!definition.isPresent()) {
                sender.sendMessage("Unknown item: " + id);
                return;
            }
            ItemDefinition item = definition.get();
            sender.sendMessage(item.id() + " type=" + item.type() + " material=" + item.material()
                    + " name=" + item.displayName() + " bound=" + item.bound()
                    + " abstract=" + item.abstractDefinition() + " parent=" + (item.parent().isPresent() ? item.parent().get() : "none"));
        }
    }

    private static final class GiveCommand implements SubCommand {
        @Override public String getName() { return "give"; }
        @Override public List<String> getAliases() { return Collections.emptyList(); }
        @Override public String getPermission() { return "voxelcore.admin.item.give"; }
        @Override public boolean playerOnly() { return false; }
        @Override public List<String> onTabComplete(CommandSender sender, String[] args) {
            List<String> ids = new ArrayList<String>();
            if (args.length == 1) {
                for (ContentID id : listableItemIds(VoxelCore.getInstance().getItemRegistry())) ids.add(id.toString());
            }
            return GiveCompletions.complete(args, ids);
        }
        @Override public void execute(CommandSender sender, String[] args) {
            if (args.length < 1) {
                sender.sendMessage("Usage: /voxelcore admin item give <content-id> [amount] [player]");
                return;
            }
            int amount = 1;
            if (args.length >= 2) {
                try { amount = Integer.parseInt(args[1]); } catch (NumberFormatException ex) {
                    sender.sendMessage("Amount must be a number."); return;
                }
            }
            if (amount < 1 || amount > 64) {
                sender.sendMessage("Amount must be between 1 and 64.");
                return;
            }
            Player target = null;
            if (args.length >= 3) target = Bukkit.getPlayerExact(args[2]);
            else if (sender instanceof Player) target = (Player) sender;
            if (target == null) {
                sender.sendMessage("A valid player is required when using this command from console.");
                return;
            }
            ContentID id = parse(args[0]);
            try {
                ItemStack stack = VoxelCore.getInstance().getItemManager().createItem(id, amount);
                target.getInventory().addItem(stack);
                sender.sendMessage("Gave " + amount + "x " + id + " to " + target.getName());
            } catch (IllegalArgumentException ex) {
                sender.sendMessage(ex.getMessage());
            }
        }
    }

    private static final class IdentifyCommand implements SubCommand {
        @Override public String getName() { return "identify"; }
        @Override public List<String> getAliases() { return Collections.singletonList("id"); }
        @Override public String getPermission() { return "voxelcore.admin.item.identify"; }
        @Override public boolean playerOnly() { return true; }
        @Override public void execute(CommandSender sender, String[] args) {
            Player player = (Player) sender;
            ItemStack held = player.getInventory().getItemInMainHand();
            Optional<ContentID> id = VoxelCore.getInstance().getItemManager().identify(held);
            sender.sendMessage(id.isPresent() ? "VoxelCore item: " + id.get() : "Held item is not a VoxelCore item.");
        }
    }

    private static final class VerifyCommand implements SubCommand {
        @Override public String getName() { return "verify"; }
        @Override public List<String> getAliases() { return Collections.singletonList("test"); }
        @Override public String getPermission() { return "voxelcore.admin.item.verify"; }
        @Override public boolean playerOnly() { return false; }
        @Override public void execute(CommandSender sender, String[] args) {
            if (args.length < 1) {
                sender.sendMessage("Usage: /voxelcore admin item verify <content-id>");
                return;
            }
            ContentID expected = parse(args[0]);
            try {
                ItemStack stack = VoxelCore.getInstance().getItemManager().createItem(expected, 1);
                Optional<ContentID> actual = VoxelCore.getInstance().getItemManager().identify(stack);
                if (actual.isPresent() && expected.equals(actual.get())) {
                    sender.sendMessage("VOXELCORE_ITEM_VERIFY_OK id=" + expected + " material=" + stack.getType().name());
                } else {
                    sender.sendMessage("VOXELCORE_ITEM_VERIFY_FAIL expected=" + expected + " actual=" + (actual.isPresent() ? actual.get() : "none"));
                }
            } catch (RuntimeException ex) {
                sender.sendMessage("VOXELCORE_ITEM_VERIFY_FAIL id=" + expected + " error=" + ex.getMessage());
            }
        }
    }
}
