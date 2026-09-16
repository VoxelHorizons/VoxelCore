package org.voxelhorizons.command.commands;

import org.bukkit.command.CommandSender;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.command.SubCommand;
import org.voxelhorizons.content.runtime.ContentReloadResult;
import org.voxelhorizons.pack.JavaPackBuildResult;
import org.voxelhorizons.pack.JavaPackTarget;
import org.voxelhorizons.pack.PackManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PackCommand implements SubCommand {
    private final Map<String, SubCommand> children = new HashMap<String, SubCommand>();

    public PackCommand() {
        register(new InfoCommand());
        register(new ValidateCommand());
        register(new BuildCommand());
    }

    private void register(SubCommand command) {
        children.put(command.getName(), command);
        for (String alias : command.getAliases()) children.put(alias, command);
    }

    @Override public String getName() { return "pack"; }
    @Override public List<String> getAliases() { return Collections.emptyList(); }
    @Override public String getPermission() { return "voxelcore.admin.pack"; }
    @Override public boolean playerOnly() { return false; }
    @Override public Map<String, SubCommand> getChildren() { return children; }
    @Override public void execute(CommandSender sender, String[] args) {
        sender.sendMessage("Usage: /voxelcore admin pack <info|validate|build> [target]");
    }

    private static JavaPackTarget target(CommandSender sender, String[] args) {
        PackManager manager = VoxelCore.getInstance().getPackManager();
        if (args.length == 0) {
            JavaPackTarget current = manager.currentTarget();
            if (current == null) {
                sender.sendMessage("No exact pack target is registered for this server version. Specify one of: " + knownTargets());
                return null;
            }
            return current;
        }
        JavaPackTarget selected = JavaPackTarget.byId(args[0]);
        if (selected == null) sender.sendMessage("Unknown pack target '" + args[0] + "'. Known targets: " + knownTargets());
        return selected;
    }

    private static String knownTargets() {
        StringBuilder out = new StringBuilder();
        for (JavaPackTarget target : JavaPackTarget.knownTargets()) {
            if (out.length() > 0) out.append(", ");
            out.append(target.id());
        }
        return out.toString();
    }

    private static final class InfoCommand implements SubCommand {
        @Override public String getName() { return "info"; }
        @Override public List<String> getAliases() { return Collections.emptyList(); }
        @Override public String getPermission() { return "voxelcore.admin.pack.info"; }
        @Override public boolean playerOnly() { return false; }
        @Override public void execute(CommandSender sender, String[] args) {
            PackManager manager = VoxelCore.getInstance().getPackManager();
            JavaPackTarget current = manager.currentTarget();
            sender.sendMessage("VoxelCore Java pack target: " + (current == null ? "no exact target for this server" : current.id() + " (format " + current.packFormat() + ")"));
            sender.sendMessage("Allocation manifest: " + manager.allocationManifest());
            sender.sendMessage("Build output: " + manager.outputRoot());
            sender.sendMessage("Known targets: " + knownTargets());
        }
    }

    private static final class ValidateCommand implements SubCommand {
        @Override public String getName() { return "validate"; }
        @Override public List<String> getAliases() { return Collections.emptyList(); }
        @Override public String getPermission() { return "voxelcore.admin.pack.validate"; }
        @Override public boolean playerOnly() { return false; }
        @Override public void execute(CommandSender sender, String[] args) {
            JavaPackTarget target = target(sender, args);
            if (target == null) return;
            try {
                JavaPackBuildResult result = VoxelCore.getInstance().getPackManager().validate(target);
                sender.sendMessage("VoxelCore pack valid for " + target.id() + ": " + result.renderedItems()
                        + " rendered items, " + result.copiedAssets() + " authored assets");
            } catch (RuntimeException exception) {
                sender.sendMessage("VoxelCore pack validation failed for " + target.id() + ": " + exception.getMessage());
            }
        }
    }

    private static final class BuildCommand implements SubCommand {
        @Override public String getName() { return "build"; }
        @Override public List<String> getAliases() { return Collections.emptyList(); }
        @Override public String getPermission() { return "voxelcore.admin.pack.build"; }
        @Override public boolean playerOnly() { return false; }
        @Override public void execute(CommandSender sender, String[] args) {
            JavaPackTarget target = target(sender, args);
            if (target == null) return;
            try {
                VoxelCore plugin = VoxelCore.getInstance();
                JavaPackBuildResult result = plugin.getPackManager().build(target);
                sender.sendMessage("VoxelCore pack built for " + target.id() + ": " + result.output()
                        + " (" + result.renderedItems() + " rendered items, " + result.copiedAssets() + " authored assets)");
                ContentReloadResult reload = plugin.onReload();
                if (reload.success()) {
                    sender.sendMessage("Published content revision " + reload.activeRevision() + " with "
                            + reload.itemCount() + " items; UI placeholders are now active.");
                } else {
                    sender.sendMessage("Pack built, but live content reload failed; revision "
                            + reload.activeRevision() + " remains active: " + reload.message());
                }
            } catch (RuntimeException exception) {
                sender.sendMessage("VoxelCore pack build failed for " + target.id() + ": " + exception.getMessage());
            }
        }
    }
}
