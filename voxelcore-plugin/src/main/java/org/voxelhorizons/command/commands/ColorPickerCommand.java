package org.voxelhorizons.command.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.command.SubCommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Self-open is administrator-only; console/block actions use the targeted form. */
public final class ColorPickerCommand implements SubCommand {
    @Override public String getName() { return "colorpicker"; }
    @Override public List<String> getAliases() { return Arrays.asList("colourpicker", "paint"); }
    @Override public String getPermission() { return null; } // Permission depends on arguments.
    @Override public boolean playerOnly() { return false; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Usage: /vc colorpicker <player>");
                return;
            }
            if (!sender.hasPermission("voxelcore.colorpicker.self")) {
                sender.sendMessage(ChatColor.RED + "You cannot open the color picker directly. Use a coloring bench.");
                return;
            }
            VoxelCore.getInstance().getColorPickerMenu().open((Player) sender);
            return;
        }
        if (args.length != 1) {
            sender.sendMessage("Usage: /vc colorpicker [player]");
            return;
        }
        if (!sender.hasPermission("voxelcore.colorpicker.others")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to open the color picker for players.");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + args[0] + "' is not online.");
            return;
        }
        VoxelCore.getInstance().getColorPickerMenu().open(target);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1 || !sender.hasPermission("voxelcore.colorpicker.others"))
            return Collections.emptyList();
        List<String> matches = new ArrayList<String>();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        for (Player player : Bukkit.getOnlinePlayers())
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) matches.add(player.getName());
        return matches;
    }
}
