package org.voxelhorizons.command;

import org.bukkit.command.CommandSender;

import java.util.List;

public interface RootCommand {

    String getName();                 // "reload"
    List<String> getAliases();         // ["rl"]
    String getPermission();            // "itemcore.admin.reload"
    boolean playerOnly();

    void execute(CommandSender sender, String[] args);
}