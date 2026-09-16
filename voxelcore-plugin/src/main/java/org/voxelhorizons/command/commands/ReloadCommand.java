package org.voxelhorizons.command.commands;

import org.bukkit.command.CommandSender;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.command.SubCommand;
import org.voxelhorizons.content.runtime.ContentReloadResult;

import java.util.Collections;
import java.util.List;

public class ReloadCommand implements SubCommand {
    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("rl");
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
        String name = VoxelCore.getInstance().getDescription().getName();
        sender.sendMessage("Reloading " + name + " content...");

        ContentReloadResult result = VoxelCore.getInstance().onReload();
        if (result.success()) {
            sender.sendMessage("Reloaded " + name + " successfully. Content revision "
                    + result.activeRevision() + " is active with " + result.itemCount() + " items and "
                    + result.blockCount() + " blocks.");
            return;
        }

        sender.sendMessage("Reload failed: " + result.message());
        sender.sendMessage("Content revision " + result.activeRevision()
                + " remains active with " + result.itemCount() + " items and " + result.blockCount() + " blocks.");
    }
}
