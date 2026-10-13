package org.voxelhorizons.command.commands;

import org.bukkit.command.CommandSender;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.command.SubCommand;
import org.voxelhorizons.content.runtime.ContentReloadResult;
import org.voxelhorizons.content.load.ContentDefinitions;
import org.voxelhorizons.pack.JavaPackBuildResult;
import org.voxelhorizons.pack.JavaPackTarget;
import org.voxelhorizons.pack.PackManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

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
        private final AtomicBoolean working = new AtomicBoolean(false);

        @Override public String getName() { return "build"; }
        @Override public List<String> getAliases() { return Collections.emptyList(); }
        @Override public String getPermission() { return "voxelcore.admin.pack.build"; }
        @Override public boolean playerOnly() { return false; }

        @Override public void execute(CommandSender sender, String[] args) {
            JavaPackTarget target = target(sender, args);
            if (target == null) return;
            if (!working.compareAndSet(false, true)) {
                sender.sendMessage("VoxelCore pack build/reload is already running.");
                return;
            }

            final VoxelCore plugin = VoxelCore.getInstance();
            final long startingRevision = plugin.getContentRuntime().current().revision();
            sender.sendMessage("VoxelCore pack build for " + target.id()
                    + " started asynchronously; the server will remain responsive.");

            try {
                // The compiler and candidate loader operate on disk/immutable Java data,
                // never touch Bukkit inventories, blocks or live mutable server state.
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    final JavaPackBuildResult output;
                    try {
                        output = plugin.getPackManager().build(target);
                    } catch (RuntimeException failure) {
                        complete(plugin, () -> sender.sendMessage("VoxelCore pack build failed for "
                                + target.id() + ": " + failure.getMessage()));
                        return;
                    }

                    ContentDefinitions candidate = null;
                    String loadError = null;
                    try {
                        candidate = plugin.prepareContentReload();
                    } catch (RuntimeException failure) {
                        loadError = failure.getMessage();
                    }
                    final ContentDefinitions prepared = candidate;
                    final String prepareError = loadError;
                    complete(plugin, () -> {
                        sender.sendMessage("VoxelCore pack built for " + target.id() + ": " + output.output()
                                + " (" + output.renderedItems() + " rendered items, " + output.renderedBlocks()
                                + " rendered blocks, " + output.copiedAssets() + " authored assets)");
                        if (prepared == null) {
                            sender.sendMessage("Pack built, but live content reload failed; revision "
                                    + plugin.getContentRuntime().current().revision()
                                    + " remains active: " + prepareError);
                            return;
                        }
                        if (plugin.getContentRuntime().current().revision() != startingRevision) {
                            sender.sendMessage("Pack built, but content changed during the build; "
                                    + "skipped publishing an outdated snapshot. Run /vc admin content reload.");
                            return;
                        }

                        // Runtime configuration, world/platform validation, allocation persistence,
                        // glyph publishing and event/plugin visibility stay on the primary thread.
                        ContentReloadResult reload = plugin.onReloadPrepared(prepared);
                        if (reload.success()) {
                            sender.sendMessage("Published content revision " + reload.activeRevision() + " with "
                                    + reload.itemCount() + " items and " + reload.blockCount()
                                    + " blocks; UI placeholders are now active.");
                        } else {
                            sender.sendMessage("Pack built, but live content reload failed; revision "
                                    + reload.activeRevision() + " remains active: " + reload.message());
                        }
                    });
                });
            } catch (RuntimeException failure) {
                working.set(false);
                sender.sendMessage("Unable to start VoxelCore pack build: " + failure.getMessage());
            }
        }

        private void complete(VoxelCore plugin, Runnable callback) {
            try {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        callback.run();
                    } catch (RuntimeException failure) {
                        plugin.getLogger().log(Level.SEVERE,
                                "Unable to finish asynchronous resource-pack reload", failure);
                    } finally {
                        working.set(false);
                    }
                });
            } catch (RuntimeException failure) {
                working.set(false);
                plugin.getLogger().log(Level.WARNING,
                        "Resource-pack completion could not be scheduled (plugin shutting down?)", failure);
            }
        }
    }
}
