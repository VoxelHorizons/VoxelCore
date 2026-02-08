package org.voxelhorizons.command;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

public interface SubCommand {

    String getName();                 // "reload"
    List<String> getAliases();         // ["rl"]
    String getPermission();            // "itemcore.admin.reload"
    boolean playerOnly();

    default Map<String, SubCommand> getChildren() {
        return Map.of();
    }

    default List<String> onTabComplete(CommandSender sender, String[] args) {
        return List.of();
    }

    void execute(CommandSender sender, String[] args);
}