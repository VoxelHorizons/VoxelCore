package org.voxelhorizons.command.commands;

import org.bukkit.command.CommandSender;
import org.voxelhorizons.PluginCore;
import org.voxelhorizons.command.RootCommand;

import java.util.List;

public class BaseCommand implements RootCommand {

    @Override
    public String getName() {
        return PluginCore.getInstance().getName().toLowerCase();
    }

    @Override
    public List<String> getAliases() {
        return List.of();
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
        sender.sendMessage(PluginCore.getInstance().getDescription().getName() + " " + PluginCore.getInstance().getDescription().getVersion());
    }
}
