package org.voxelhorizons.command;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface SubCommand {
    String getName();
    List<String> getAliases();
    String getPermission();
    boolean playerOnly();

    default Map<String, SubCommand> getChildren() {
        return Collections.emptyMap();
    }

    default List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    void execute(CommandSender sender, String[] args);
}
