package org.voxelhorizons.command.commands;

import org.bukkit.command.CommandSender;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.command.SubCommand;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminCommand implements SubCommand {

    private final Map<String, SubCommand> children = new HashMap<String, SubCommand>();

    public AdminCommand() {
        register(new ReloadCommand());
        register(new ContentCommand());
        register(new ItemCommand());
        register(new BlockCommand());
        register(new PackCommand());
        register(new UiCommand());
    }

    private void register(SubCommand command) {
        children.put(command.getName(), command);
        for (String alias : command.getAliases()) {
            children.put(alias, command);
        }
    }

    @Override
    public String getName() { return "admin"; }

    @Override
    public List<String> getAliases() { return Collections.singletonList("a"); }

    @Override
    public String getPermission() { return VoxelCore.getInstance().getDescription().getName().toLowerCase() + ".admin"; }

    @Override
    public boolean playerOnly() { return false; }

    @Override
    public Map<String, SubCommand> getChildren() { return children; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage("Usage: /voxelcore admin <reload|content|item|block|pack|ui>");
    }
}
