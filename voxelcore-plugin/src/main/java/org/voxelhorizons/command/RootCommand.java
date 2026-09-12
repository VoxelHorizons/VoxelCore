package org.voxelhorizons.command;

import org.bukkit.command.CommandSender;

import java.util.List;

public interface RootCommand {
    String getName();
    List<String> getAliases();
    String getPermission();
    boolean playerOnly();
    void execute(CommandSender sender, String[] args);
}
