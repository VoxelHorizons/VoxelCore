package org.voxelhorizons.command.commands;

import org.bukkit.command.CommandSender;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.command.SubCommand;
import org.voxelhorizons.content.runtime.ContentReloadResult;
import org.voxelhorizons.content.runtime.ContentSnapshot;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ContentCommand implements SubCommand {
    private final Map<String, SubCommand> children = new HashMap<String, SubCommand>();

    public ContentCommand() {
        register(new InfoCommand());
        register(new ReloadContentCommand());
    }

    private void register(SubCommand command) {
        children.put(command.getName(), command);
        for (String alias : command.getAliases()) children.put(alias, command);
    }

    @Override public String getName() { return "content"; }
    @Override public List<String> getAliases() { return Collections.emptyList(); }
    @Override public String getPermission() { return "voxelcore.admin.content"; }
    @Override public boolean playerOnly() { return false; }
    @Override public Map<String, SubCommand> getChildren() { return children; }
    @Override public void execute(CommandSender sender, String[] args) {
        sender.sendMessage("Usage: /voxelcore admin content <info|reload>");
    }

    private static final class InfoCommand implements SubCommand {
        @Override public String getName() { return "info"; }
        @Override public List<String> getAliases() { return Collections.emptyList(); }
        @Override public String getPermission() { return "voxelcore.admin.content.info"; }
        @Override public boolean playerOnly() { return false; }
        @Override public void execute(CommandSender sender, String[] args) {
            ContentSnapshot snapshot = VoxelCore.getInstance().getContentRuntime().current();
            sender.sendMessage("VoxelCore content revision " + snapshot.revision() + ": " + snapshot.items().size() + " items");
        }
    }

    private static final class ReloadContentCommand implements SubCommand {
        @Override public String getName() { return "reload"; }
        @Override public List<String> getAliases() { return Collections.singletonList("rl"); }
        @Override public String getPermission() { return "voxelcore.admin.content.reload"; }
        @Override public boolean playerOnly() { return false; }
        @Override public void execute(CommandSender sender, String[] args) {
            ContentReloadResult result = VoxelCore.getInstance().onReload();
            if (result.success()) {
                sender.sendMessage("VoxelCore content reloaded: revision " + result.activeRevision() + ", "
                        + result.itemCount() + " items, " + result.blockCount() + " blocks");
            } else {
                sender.sendMessage("VoxelCore content reload failed; revision " + result.activeRevision() + " remains active: " + result.message());
            }
        }
    }
}
