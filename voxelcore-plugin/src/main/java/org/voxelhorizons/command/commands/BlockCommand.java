package org.voxelhorizons.command.commands;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.command.SubCommand;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.block.BlockAllocation;
import org.voxelhorizons.content.block.BlockDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BlockCommand implements SubCommand {
    private final Map<String, SubCommand> children = new HashMap<String, SubCommand>();

    public BlockCommand() {
        register(new ListCommand()); register(new InfoCommand()); register(new GiveCommand());
        register(new BrowserCommand()); register(new IdentifyCommand());
    }
    private void register(SubCommand command) {
        children.put(command.getName(), command);
        for (String alias : command.getAliases()) children.put(alias, command);
    }
    @Override public String getName() { return "block"; }
    @Override public List<String> getAliases() { return Collections.singletonList("blocks"); }
    @Override public String getPermission() { return "voxelcore.admin.block"; }
    @Override public boolean playerOnly() { return false; }
    @Override public Map<String, SubCommand> getChildren() { return children; }
    @Override public void execute(CommandSender sender, String[] args) {
        sender.sendMessage("Usage: /voxelcore admin block <list|info|give|ui|identify>");
    }

    private static ContentID id(String value) { return ContentID.parse(value, "voxelhorizons"); }

    private static final class ListCommand implements SubCommand {
        @Override public String getName() { return "list"; }
        @Override public List<String> getAliases() { return Collections.emptyList(); }
        @Override public String getPermission() { return "voxelcore.admin.block.list"; }
        @Override public boolean playerOnly() { return false; }
        @Override public void execute(CommandSender sender, String[] args) {
            List<ContentID> ids = new ArrayList<ContentID>();
            for (BlockDefinition definition : VoxelCore.getInstance().getBlockRegistry().entries().values()) {
                if (!definition.abstractDefinition()) ids.add(definition.id());
            }
            Collections.sort(ids, new Comparator<ContentID>() {
                @Override public int compare(ContentID left, ContentID right) { return left.toString().compareTo(right.toString()); }
            });
            sender.sendMessage("VoxelCore blocks (" + ids.size() + "): " + ids);
        }
    }

    private static final class InfoCommand implements SubCommand {
        @Override public String getName() { return "info"; }
        @Override public List<String> getAliases() { return Collections.emptyList(); }
        @Override public String getPermission() { return "voxelcore.admin.block.info"; }
        @Override public boolean playerOnly() { return false; }
        @Override public void execute(CommandSender sender, String[] args) {
            if (args.length < 1) { sender.sendMessage("Usage: /voxelcore admin block info <content-id>"); return; }
            ContentID id = id(args[0]);
            BlockDefinition definition = VoxelCore.getInstance().getBlockRegistry().get(id).orElse(null);
            BlockAllocation allocation = VoxelCore.getInstance().getContentRuntime().current().blockAllocations().get(id).orElse(null);
            if (definition == null) { sender.sendMessage("Unknown block: " + id); return; }
            sender.sendMessage(id + " method=" + definition.method() + " model=" + definition.model()
                    + " hardness=" + definition.hardness() + " breakTools=" + definition.breakTools()
                    + " minimumToolTier=" + definition.minimumToolTier()
                    + " stackable=" + definition.stackable() + " allocation="
                    + (allocation == null ? "none" : allocation.method() + ":" + allocation.slot()));
        }
    }

    private static final class GiveCommand implements SubCommand {
        @Override public String getName() { return "give"; }
        @Override public List<String> getAliases() { return Collections.emptyList(); }
        @Override public String getPermission() { return "voxelcore.admin.block.give"; }
        @Override public boolean playerOnly() { return false; }
        @Override public List<String> onTabComplete(CommandSender sender, String[] args) {
            List<String> ids = new ArrayList<String>();
            if (args.length == 1) {
                for (BlockDefinition definition : VoxelCore.getInstance().getBlockRegistry().entries().values()) {
                    if (!definition.abstractDefinition() && VoxelCore.getInstance().getItemRegistry().get(definition.id())
                            .filter(item -> !item.abstractDefinition()).isPresent()) ids.add(definition.id().toString());
                }
            }
            return GiveCompletions.complete(args, ids);
        }
        @Override public void execute(CommandSender sender, String[] args) {
            if (args.length < 1) { sender.sendMessage("Usage: /voxelcore admin block give <content-id> [amount] [player]"); return; }
            ContentID id = id(args[0]);
            if (!VoxelCore.getInstance().getBlockRegistry().contains(id)) { sender.sendMessage("Unknown block: " + id); return; }
            int amount = 1;
            try { if (args.length > 1) amount = Integer.parseInt(args[1]); }
            catch (NumberFormatException exception) { sender.sendMessage("Amount must be a number."); return; }
            if (amount < 1 || amount > 64) { sender.sendMessage("Amount must be between 1 and 64."); return; }
            Player player = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player ? (Player) sender : null;
            if (player == null) { sender.sendMessage("A valid player is required."); return; }
            try {
                ItemStack stack = VoxelCore.getInstance().getItemManager().createItem(id, amount);
                player.getInventory().addItem(stack);
                sender.sendMessage("Gave " + amount + "x " + id + " block item to " + player.getName());
            } catch (IllegalArgumentException exception) {
                sender.sendMessage("Block " + id + " needs a matching item definition before it can be given.");
            }
        }
    }

    private static final class BrowserCommand implements SubCommand {
        @Override public String getName() { return "ui"; }
        @Override public List<String> getAliases() { return java.util.Arrays.asList("gui", "menu"); }
        @Override public String getPermission() { return "voxelcore.admin.block.ui"; }
        @Override public boolean playerOnly() { return true; }
        @Override public void execute(CommandSender sender, String[] args) {
            VoxelCore.getInstance().getContentBrowser().openBlocks((Player) sender);
        }
    }

    private static final class IdentifyCommand implements SubCommand {
        @Override public String getName() { return "identify"; }
        @Override public List<String> getAliases() { return Collections.singletonList("id"); }
        @Override public String getPermission() { return "voxelcore.admin.block.identify"; }
        @Override public boolean playerOnly() { return true; }
        @Override public void execute(CommandSender sender, String[] args) {
            Player player = (Player) sender;
            Block target = player.getTargetBlock(null, 6);
            Optional<ContentID> id = VoxelCore.getInstance().getBlockManager().identify(target);
            sender.sendMessage(id.isPresent() ? "VoxelCore block: " + id.get() : "Target is not a VoxelCore block.");
        }
    }
}
