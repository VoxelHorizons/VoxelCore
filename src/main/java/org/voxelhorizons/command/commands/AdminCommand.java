package org.voxelhorizons.command.commands;

import org.bukkit.command.CommandSender;
import org.voxelhorizons.PluginCore;
import org.voxelhorizons.command.SubCommand;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminCommand implements SubCommand {

    private final Map<String, SubCommand> children = new HashMap<>();

    public AdminCommand() {
        register(new ReloadCommand());
    }

    private void register(SubCommand cmd) {
        children.put(cmd.getName(), cmd);
        for (String alias : cmd.getAliases()) {
            children.put(alias, cmd);
        }
    }

    @Override
    public String getName() {
        return "admin";
    }

    @Override
    public List<String> getAliases() {
        return List.of("a");
    }

    @Override
    public String getPermission() {
        return PluginCore.getInstance().getDescription().getName().toLowerCase() + ".admin";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage("Reloading " + PluginCore.getInstance().getDescription().getName() + "...");
    }
}
