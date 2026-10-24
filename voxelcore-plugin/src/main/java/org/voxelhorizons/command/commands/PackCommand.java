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
        register(new GlyphsCommand());
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
        sender.sendMessage("Usage: /voxelcore admin pack <info|host|send|validate|build|glyphs> [player|target]");
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

    /**
     * Progress sink for async pack builds: always logged (console/log), and echoed to the
     * player who started the build, on the main thread.
     */
    private static org.voxelhorizons.pack.BuildProgress buildProgress(VoxelCore plugin, CommandSender sender) {
        return message -> {
            plugin.getLogger().info("[pack build] " + message);
            if (sender instanceof org.bukkit.entity.Player) {
                try {
                    plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage("[VoxelCore] " + message));
                } catch (RuntimeException disabled) {
                    // Plugin disabling mid-build: the log line above is enough.
                }
            }
        };
    }

    /**
     * bedrock.vanilla_assets: 'auto' downloads and caches the official client jar for this
     * server's Minecraft version; 'off' disables; anything else is a client jar or extracted
     * assets folder path. Called from the async build task only.
     */
    private static java.nio.file.Path vanillaAssets(VoxelCore plugin, org.voxelhorizons.pack.BuildProgress progress)
            throws java.io.IOException {
        String setting = plugin.getConfig().getString("bedrock.vanilla_assets", "auto").trim();
        if (setting.isEmpty() || setting.equalsIgnoreCase("off")) return null;
        if (!setting.equalsIgnoreCase("auto")) {
            java.nio.file.Path path = java.nio.file.Paths.get(setting);
            if (!java.nio.file.Files.exists(path)) throw new java.io.IOException("bedrock.vanilla_assets not found: " + path);
            return path;
        }
        String version = org.voxelhorizons.platform.MinecraftVersionDetector.detect();
        return org.voxelhorizons.pack.VanillaClientJar.resolve(
                plugin.getDataFolder().toPath().resolve("cache").resolve("vanilla"), version, progress);
    }

    /**
     * Resolve only a genuinely loaded Bukkit Geyser plugin. Scanning stale folders in
     * plugins/ would risk silently publishing into an old backend copy when the live
     * Geyser instance actually runs on a Velocity/Bungee proxy.
     * Call on the server thread, not from the async pack builder.
     */
    private static java.nio.file.Path localGeyserDataDirectory(VoxelCore plugin) {
        for (org.bukkit.plugin.Plugin installed : plugin.getServer().getPluginManager().getPlugins()) {
            String name = installed.getName();
            if ("Geyser-Spigot".equalsIgnoreCase(name) || "Geyser".equalsIgnoreCase(name)) {
                return installed.getDataFolder().toPath();
            }
        }
        return null;
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
            VoxelCore plugin = VoxelCore.getInstance();
            org.voxelhorizons.pack.GeyserPackPublisher.Destination destination =
                    org.voxelhorizons.pack.GeyserPackPublisher.resolveDestination(
                            plugin.getConfig().getString("bedrock.geyser_data_directory", ""),
                            plugin.getConfig().getBoolean("bedrock.auto_publish_local", true),
                            localGeyserDataDirectory(plugin));
            sender.sendMessage("Bedrock builds: " + plugin.getConfig().getBoolean("game.bedrock_support", false)
                    + "; Geyser publish target: " + destination.explanation);
            if (destination.root != null) {
                java.nio.file.Path pack = destination.root.resolve("packs/voxelcore.mcpack");
                java.nio.file.Path mappings = destination.root.resolve("custom_mappings/voxelcore-items.json");
                sender.sendMessage("Geyser pack: " + pack + " ("
                        + (java.nio.file.Files.isRegularFile(pack) ? "present" : "missing")
                        + "); mappings: " + mappings + " ("
                        + (java.nio.file.Files.isRegularFile(mappings) ? "present" : "missing")
                        + "). Geyser restart and Bedrock reconnect required after updating.");
                try {
                    org.voxelhorizons.pack.GeyserPackPublisher.PublicationState state =
                            org.voxelhorizons.pack.GeyserPackPublisher.inspect(destination.root);
                    sender.sendMessage("Geyser VoxelCore publication: " + state);
                    if (!"VERIFIED_ON_DISK".equals(state.status))
                        sender.sendMessage(org.bukkit.ChatColor.YELLOW
                                + "Geyser files are not verified as a matching VoxelCore revision.");
                } catch (java.io.IOException inspection) {
                    sender.sendMessage(org.bukkit.ChatColor.RED
                            + "Unable to verify Geyser publication: " + inspection.getMessage());
                }
            }
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
            if (org.voxelhorizons.pack.BedrockPlayers.isBedrock(target.getUniqueId())) {
                sender.sendMessage("Bedrock packs are supplied by Geyser at connection; restart Geyser after publishing and reconnect this player.");
                return;
            }
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

    /**
     * B14: find (and, with explicit confirmation, rewrite) unmanaged literal glyphs in
     * Bedrock's reserved U+E000..U+E1FF range across VoxelCore content, DeluxeMenus and
     * configured extra roots. Font definitions and references move together; originals
     * are backed up once. File work runs off the server thread.
     */
    private static final class GlyphsCommand implements SubCommand {
        private final AtomicBoolean working = new AtomicBoolean(false);

        @Override public String getName() { return "glyphs"; }
        @Override public List<String> getAliases() { return Collections.emptyList(); }
        @Override public String getPermission() { return "voxelcore.admin.pack.glyphs"; }
        @Override public boolean playerOnly() { return false; }
        @Override public List<String> onTabComplete(CommandSender sender, String[] args) {
            return args.length <= 1 ? java.util.Arrays.asList("scan", "migrate") : Collections.<String>emptyList();
        }

        @Override public void execute(CommandSender sender, String[] args) {
            String mode = args.length == 0 ? "scan" : args[0].toLowerCase(java.util.Locale.ROOT);
            final boolean migrate = "migrate".equals(mode);
            if (!migrate && !"scan".equals(mode)) {
                sender.sendMessage("Usage: /voxelcore admin pack glyphs <scan|migrate confirm>");
                return;
            }
            if (migrate && (args.length < 2 || !"confirm".equalsIgnoreCase(args[1]))) {
                sender.sendMessage("This rewrites font JSON and menu/text files in place (originals backed up once). "
                        + "Run '/voxelcore admin pack glyphs scan' first, then 'glyphs migrate confirm'.");
                return;
            }
            final VoxelCore plugin = VoxelCore.getInstance();
            final java.nio.file.Path data = plugin.getDataFolder().toPath();
            final java.nio.file.Path pluginsRoot = data.getParent();
            final List<java.nio.file.Path> roots = new java.util.ArrayList<java.nio.file.Path>();
            roots.add(plugin.getPackManager().contentRoot());
            for (String configured : plugin.getConfig().getStringList("bedrock.glyph_migration_roots")) {
                if (configured == null || configured.trim().isEmpty()) continue;
                java.nio.file.Path path = java.nio.file.Paths.get(configured.trim());
                roots.add((path.isAbsolute() ? path : pluginsRoot.resolve(path)).normalize());
            }
            final java.nio.file.Path manifest = plugin.getPackManager().glyphAllocationManifest();
            if (!working.compareAndSet(false, true)) {
                sender.sendMessage("A VoxelCore glyph scan/migration is already running.");
                return;
            }
            try {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    String message;
                    try {
                        message = run(roots, manifest, data, migrate);
                    } catch (Exception failure) {
                        plugin.getLogger().log(Level.WARNING, "Glyph literal " + (migrate ? "migration" : "scan") + " failed", failure);
                        message = "Glyph literal " + (migrate ? "migration" : "scan") + " FAILED: " + failure.getMessage();
                    } finally {
                        working.set(false);
                    }
                    final String result = message;
                    try { Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(result)); }
                    catch (RuntimeException shuttingDown) { plugin.getLogger().info(result); }
                });
            } catch (RuntimeException failure) {
                working.set(false);
                sender.sendMessage("Unable to start glyph " + mode + ": " + failure.getMessage());
            }
        }

        private static String run(List<java.nio.file.Path> roots, java.nio.file.Path manifest,
                                  java.nio.file.Path data, boolean migrate) throws java.io.IOException {
            java.nio.file.Path reportFile = data.resolve("glyph-literal-migration.txt");
            org.voxelhorizons.pack.GlyphLiteralMigration.Result result;
            if (migrate) {
                result = org.voxelhorizons.pack.GlyphLiteralMigration.migrate(roots,
                        org.voxelhorizons.pack.GlyphLiteralMigration.occupied(roots.get(0), manifest),
                        data.resolve("glyph-literal-backup"));
            } else {
                List<org.voxelhorizons.pack.GlyphLiteralMigration.Finding> findings =
                        org.voxelhorizons.pack.GlyphLiteralMigration.scan(roots);
                result = org.voxelhorizons.pack.GlyphLiteralMigration.preview(findings,
                        org.voxelhorizons.pack.GlyphLiteralMigration.occupied(roots.get(0), manifest),
                        data.resolve("glyph-literal-backup"));
            }
            java.nio.file.Files.write(reportFile, org.voxelhorizons.pack.GlyphLiteralMigration.report(result)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String summary = result.findings.size() + " Bedrock-reserved glyph occurrences, "
                    + result.plan.size() + " distinct codepoints across " + roots.size() + " roots. Report: " + reportFile;
            return migrate
                    ? "Glyph migration rewrote " + result.rewritten.size() + " files; " + summary
                      + ". Rebuild the pack and reload DeluxeMenus/other plugins to apply."
                    : "Glyph scan (no files changed): " + summary;
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
            // Snapshot Bukkit configuration before handing disk work to the async worker.
            final boolean bedrockEnabled = plugin.getConfig().getBoolean("game.bedrock_support", false);
            final byte[] remoteSecret = plugin.getConfig().getString("bedrock.remote_sync.secret", "").trim()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            final org.voxelhorizons.pack.GeyserPackPublisher.Destination geyserDestination =
                    org.voxelhorizons.pack.GeyserPackPublisher.resolveDestination(
                            plugin.getConfig().getString("bedrock.geyser_data_directory", ""),
                            plugin.getConfig().getBoolean("bedrock.auto_publish_local", true),
                            localGeyserDataDirectory(plugin));
            final long startingRevision = plugin.getContentRuntime().current().revision();
            sender.sendMessage("VoxelCore pack build for " + target.id()
                    + " started asynchronously; the server will remain responsive.");

            try {
                // The compiler and candidate loader operate on disk/immutable Java data,
                // never touch Bukkit inventories, blocks or live mutable server state.
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    final org.voxelhorizons.pack.BuildProgress progress = buildProgress(plugin, sender);
                    final JavaPackBuildResult output;
                    try {
                        progress.report("Java pack: compiling " + target.id());
                        long javaStarted = System.nanoTime();
                        output = plugin.getPackManager().build(target);
                        progress.report("Java pack: done in " + String.format(java.util.Locale.ROOT, "%.1f",
                                (System.nanoTime() - javaStarted) / 1e9) + "s");
                    } catch (RuntimeException failure) {
                        complete(plugin, () -> sender.sendMessage("VoxelCore pack build failed for "
                                + target.id() + ": " + failure.getMessage()));
                        return;
                    }
                    JavaPackTarget serverTarget = plugin.getPackManager().currentTarget();
                    final boolean currentServerTarget = serverTarget != null && serverTarget.id().equals(target.id());
                    String bedrockStatus = "";
                    if (currentServerTarget && bedrockEnabled) {
                        try {
                            // 1.21.4+: item_model definitions; 1.14-1.21.3: Geyser legacy custom_model_data definitions.
                            String vanillaNote = "";
                            java.nio.file.Path vanilla = null;
                            try {
                                vanilla = vanillaAssets(plugin, progress);
                            } catch (Exception unavailable) {
                                vanillaNote = " Vanilla textures unavailable (" + unavailable.getMessage()
                                        + "); items using minecraft: textures may lack icons/3D.";
                                plugin.getLogger().warning("Bedrock vanilla assets unavailable: " + unavailable.getMessage());
                            }
                            org.voxelhorizons.pack.BedrockPackCompiler.Result bedrock =
                                    new org.voxelhorizons.pack.BedrockPackCompiler().withVanillaAssets(vanilla)
                                            .withProgress(progress).compile(
                                            plugin.getPackManager().contentRoot(),
                                            plugin.getPackManager().outputRoot().resolve("bedrock"),
                                            plugin.getPackManager().glyphAllocationManifest(),
                                            target.mode());
                            bedrockStatus = "Bedrock built: " + bedrock.directory + "; " + bedrock.mappedItems
                                    + " mapped items; " + bedrock.mappedGlyphs + " Bedrock inline glyphs; "
                                    + bedrock.nativeGeometryCount + " native 3D attachables; "
                                    + bedrock.unsupportedGeometryCount + " 3D limitations (unsupported-geometry.txt); "
                                    + bedrock.unsupportedItems + " items unsupported (unsupported-items.txt)."
                                    + (bedrock.unsupportedReasonSummary.isEmpty() ? ""
                                    : " Reasons: " + bedrock.unsupportedReasonSummary + ".")
                                    + vanillaNote;
                            plugin.loadBedrockGlyphTiles();
                            if (geyserDestination.root != null) {
                                progress.report("Bedrock: publishing to Geyser at " + geyserDestination.root);
                                new org.voxelhorizons.pack.GeyserPackPublisher().publish(bedrock, geyserDestination.root);
                                java.nio.file.Path pack = geyserDestination.root.resolve("packs/voxelcore.mcpack");
                                java.nio.file.Path mappings = geyserDestination.root.resolve("custom_mappings/voxelcore-items.json");
                                bedrockStatus += " Published to " + geyserDestination.source
                                        + ": " + pack + " (" + java.nio.file.Files.size(pack) + " bytes), "
                                        + mappings + " (" + java.nio.file.Files.size(mappings)
                                        + " bytes). Publication receipt: "
                                        + org.voxelhorizons.pack.GeyserPackPublisher.inspect(geyserDestination.root)
                                        + ". Restart Geyser, then reconnect Bedrock clients.";
                            } else {
                                bedrockStatus += " Export only: " + geyserDestination.explanation;
                            }
                            if (remoteSecret.length > 0) {
                                // B17: signed bundle for Geyser on a proxy/other host, served at /bedrock/.
                                java.nio.file.Path remote = plugin.getPackManager().outputRoot().resolve("bedrock-remote");
                                org.voxelhorizons.pack.RemoteBedrockBundle.export(bedrock, remoteSecret, remote,
                                        System.currentTimeMillis());
                                plugin.getResourcePackHost().setBedrockRemote(remote, remoteSecret);
                                bedrockStatus += " Signed remote bundle exported"
                                        + (plugin.getResourcePackHost().isRunning() ? " and served at /bedrock/."
                                        : "; enable resource_pack.host to serve it.");
                            }
                        } catch (Exception failure) {
                            bedrockStatus = "Bedrock build/publish FAILED: " + failure.getMessage();
                            plugin.getLogger().log(Level.WARNING, bedrockStatus, failure);
                        }
                    }
                    final String bedrockMessage = bedrockStatus;
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
                        if (!bedrockMessage.isEmpty()) sender.sendMessage(bedrockMessage);
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
