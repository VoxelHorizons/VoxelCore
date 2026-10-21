package org.voxelhorizons.command.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.command.SubCommand;
import org.voxelhorizons.content.runtime.ContentReloadResult;
import org.voxelhorizons.content.load.ContentDefinitions;
import org.voxelhorizons.pack.JavaPackBuildResult;
import org.voxelhorizons.pack.JavaPackTarget;
import org.voxelhorizons.pack.PackManager;
import org.voxelhorizons.pack.ResourcePackHost;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.logging.Level;

public final class PackCommand implements SubCommand {
    private final Map<String, SubCommand> children = new HashMap<String, SubCommand>();

    public PackCommand() {
        register(new InfoCommand());
        register(new HostCommand());
        register(new SendCommand());
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
        sender.sendMessage("Usage: /voxelcore admin pack <info|host|send|validate|build> [player|target]");
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
            sender.sendMessage("Self-hosted delivery: " + (VoxelCore.getInstance().getResourcePackHost().isRunning()
                    ? "listening on port " + VoxelCore.getInstance().getConfig().getInt("resource_pack.host.port", 24464)
                    : "disabled; enable resource_pack.host.enabled in config.yml"));
            sender.sendMessage("Hosted URL: " + (VoxelCore.getInstance().getPlayerResourcePackService() == null
                    ? "unavailable" : VoxelCore.getInstance().getPlayerResourcePackService().configuredPackUrl()));
            sender.sendMessage("Hosted file: " + VoxelCore.getInstance().getResourcePackHost().hostedPack()
                    + " (" + hostedFileSize(VoxelCore.getInstance().getResourcePackHost()) + " bytes; SHA-1 "
                    + VoxelCore.getInstance().getResourcePackHost().sha1Hex() + ")");
            sender.sendMessage("Known targets: " + knownTargets());
        }
    }

    private static String hostedFileSize(ResourcePackHost host) {
        try {
            long size = host.hostedPackSize();
            return size < 0 ? "missing" : Long.toString(size);
        } catch (Exception failure) {
            return "unavailable: " + failure.getMessage();
        }
    }

    private static final class HostCommand implements SubCommand {
        @Override public String getName() { return "host"; }
        @Override public List<String> getAliases() { return Collections.emptyList(); }
        @Override public String getPermission() { return "voxelcore.admin.pack.host"; }
        @Override public boolean playerOnly() { return false; }

        @Override public void execute(CommandSender sender, String[] args) {
            VoxelCore plugin = VoxelCore.getInstance();
            ResourcePackHost host = plugin.getResourcePackHost();
            sender.sendMessage("Resource-pack host: " + (host.isRunning() ? "RUNNING" : "STOPPED"));
            sender.sendMessage("Configured bind: " + plugin.getConfig().getString("resource_pack.host.bind_address", "0.0.0.0")
                    + ":" + plugin.getConfig().getInt("resource_pack.host.port", 24464)
                    + "; active socket: " + (host.isRunning() ? host.bindAddress() + ":" + host.port() : "none"));
            sender.sendMessage("Advertised URL: " + plugin.getPlayerResourcePackService().configuredPackUrl());
            sender.sendMessage("Pack file: " + host.hostedPack() + "; bytes=" + hostedFileSize(host)
                    + "; SHA-1=" + host.sha1Hex());
            if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
                sender.sendMessage("Run /vc admin pack host test to download the pack through the local listener.");
                return;
            }
            if (!"test".equalsIgnoreCase(args[0])) {
                sender.sendMessage("Usage: /vc admin pack host [status|test]");
                return;
            }
            testLocalHost(sender, plugin, host);
        }

        private void testLocalHost(CommandSender sender, VoxelCore plugin, ResourcePackHost host) {
            if (!host.isRunning()) {
                sender.sendMessage("Local HTTP test failed: host is stopped. See startup log for the bind error.");
                return;
            }
            if (host.sha1() == null) {
                sender.sendMessage("Local HTTP test failed: no pack is published. Run /vc admin pack build.");
                return;
            }
            String bind = host.bindAddress();
            String connectHost = bind == null || bind.isEmpty() || "0.0.0.0".equals(bind) || "::".equals(bind)
                    ? "127.0.0.1" : bind;
            if (connectHost.contains(":" ) && !connectHost.startsWith("[")) connectHost = "[" + connectHost + "]";
            HttpURLConnection connection = null;
            try {
                URL url = new URL("http://" + connectHost + ":" + host.port() + ResourcePackHost.RESOURCE_PATH);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(10000);
                connection.setRequestMethod("GET");
                int status = connection.getResponseCode();
                if (status != 200) {
                    sender.sendMessage("Local HTTP test failed: GET " + url + " returned HTTP " + status);
                    return;
                }
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                long bytes = 0;
                try (java.io.InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        digest.update(buffer, 0, count);
                        bytes += count;
                    }
                }
                String actualHash = toHex(digest.digest());
                boolean matches = actualHash.equals(host.sha1Hex());
                sender.sendMessage("Local HTTP test " + (matches ? "PASSED" : "FAILED") + ": GET " + url
                        + " returned HTTP 200, downloaded " + bytes + " bytes; SHA-1 "
                        + (matches ? "matches" : "does not match the published pack"));
                if (!matches) plugin.getLogger().warning("Local resource-pack HTTP test returned SHA-1 " + actualHash
                        + " but current hosted SHA-1 is " + host.sha1Hex());
            } catch (Exception failure) {
                sender.sendMessage("Local HTTP test failed: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
                plugin.getLogger().log(Level.WARNING, "Local resource-pack HTTP self-test failed", failure);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }

        private static String toHex(byte[] bytes) {
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            return out.toString();
        }
    }

    private static final class SendCommand implements SubCommand {
        @Override public String getName() { return "send"; }
        @Override public List<String> getAliases() { return Collections.singletonList("push"); }
        @Override public String getPermission() { return "voxelcore.admin.pack.send"; }
        @Override public boolean playerOnly() { return false; }

        @Override public List<String> onTabComplete(CommandSender sender, String[] args) {
            if (args.length != 1) return Collections.emptyList();
            String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
            java.util.ArrayList<String> names = new java.util.ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) names.add(player.getName());
            }
            Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
            return names;
        }

        @Override public void execute(CommandSender sender, String[] args) {
            if (args.length != 1) {
                sender.sendMessage("Usage: /voxelcore admin pack send <online-player>");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("Player '" + args[0] + "' is not online. Player pack requests can only be sent to online players.");
                return;
            }
            VoxelCore plugin = VoxelCore.getInstance();
            ResourcePackHost host = plugin.getResourcePackHost();
            if (!host.isRunning()) {
                sender.sendMessage("Pack not sent: resource-pack HTTP host is stopped. Check /vc admin pack host and startup logs.");
                return;
            }
            if (host.sha1() == null) {
                sender.sendMessage("Pack not sent: no pack is published. Run /vc admin pack build first.");
                return;
            }
            if (plugin.getPlayerResourcePackService().configuredPackUrl() == null) {
                sender.sendMessage("Pack not sent: configure resource_pack.host.public_url or public_host to an address the player can reach.");
                return;
            }
            if (plugin.getPlayerResourcePackService().send(target)) {
                sender.sendMessage("Sent the current resource-pack request to " + target.getName()
                        + ". This confirms the request was issued; check the host request log for the player's download connection.");
            } else {
                sender.sendMessage("Pack request failed for " + target.getName() + ". See the server log for details.");
            }
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
                    JavaPackTarget serverTarget = plugin.getPackManager().currentTarget();
                    final boolean currentServerTarget = serverTarget != null && serverTarget.id().equals(target.id());
                    String hostPublishError = null;
                    if (currentServerTarget) {
                        try {
                            plugin.getResourcePackHost().publish(output.output());
                        } catch (Exception failure) {
                            hostPublishError = failure.getMessage();
                        }
                    }
                    final String publishError = hostPublishError;

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
                        if (!currentServerTarget) {
                            sender.sendMessage("This target differs from the running server version; it was not published to players.");
                        } else if (publishError != null) {
                            sender.sendMessage("Pack built, but self-hosted pack file was not updated: " + publishError);
                        } else {
                            sender.sendMessage("Updated self-hosted pack file: " + plugin.getResourcePackHost().hostedPack());
                        }
                        if (currentServerTarget && publishError == null && plugin.getResourcePackHost().isRunning()) {
                            plugin.getPlayerResourcePackService().sendUpdatedPackToOnlinePlayers();
                        }
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
