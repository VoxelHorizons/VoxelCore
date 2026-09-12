package org.voxelhorizons.command.commands;

import org.bukkit.command.CommandSender;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.command.RootCommand;

import java.util.Collections;
import java.util.List;

public class BaseCommand implements RootCommand {
    @Override
    public String getName() {
        return VoxelCore.getInstance().getName().toLowerCase();
    }

    @Override
    public List<String> getAliases() {
        return Collections.emptyList();
    }

    @Override
    public String getPermission() {
        return getName() + ".use";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage(VoxelCore.getInstance().getDescription().getName() + " " + VoxelCore.getInstance().getDescription().getVersion());
    }
}
