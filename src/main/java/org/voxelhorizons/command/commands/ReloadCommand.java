package org.voxelhorizons.command.commands;

import org.bukkit.command.CommandSender;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.command.SubCommand;

import java.util.List;

public class ReloadCommand implements SubCommand {

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public List<String> getAliases() {
        return List.of("rl");
    }

    @Override
    public String getPermission() {
        return VoxelCore.getInstance().getDescription().getName().toLowerCase() + ".admin.reload";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage("Reloading " + VoxelCore.getInstance().getDescription().getName() + "...");
        VoxelCore.getInstance().onReload();
        sender.sendMessage("Reloaded " + VoxelCore.getInstance().getDescription().getName() + " Successfully.");
    }
}
