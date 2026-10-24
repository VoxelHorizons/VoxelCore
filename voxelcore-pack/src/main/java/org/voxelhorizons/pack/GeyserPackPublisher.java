package org.voxelhorizons.pack;

import java.io.IOException;
import java.nio.file.*;

/** Installs only VoxelCore-owned files. Geyser must be stopped/restarted for registry changes. */
public final class GeyserPackPublisher {
    /** A resolved Geyser target, or an actionable explanation for export-only mode. */
    public static final class Destination {
        public final Path root;
        public final String source;
        public final String explanation;

        private Destination(Path root, String source, String explanation) {
            this.root = root;
            this.source = source;
            this.explanation = explanation;
        }
    }

    /**
     * Manual paths override local detection. Do not guess proxy directories: a local plugin
     * data folder is trustworthy only when it comes from an actually loaded Geyser plugin.
     * The Bukkit lookup must happen on the main thread, before the asynchronous build.
     */
    public static Destination resolveDestination(String configuredDirectory,
                                                 boolean autoPublishLocal,
                                                 Path installedGeyserDataDirectory) {
        String configured = configuredDirectory == null ? "" : configuredDirectory.trim();
        if (!configured.isEmpty()) {
            Path root = Paths.get(configured).toAbsolutePath().normalize();
            return new Destination(root, "configured path", "Manual Geyser data directory: " + root);
        }
        if (autoPublishLocal && installedGeyserDataDirectory != null) {
            Path root = installedGeyserDataDirectory.toAbsolutePath().normalize();
            return new Destination(root, "detected local plugin", "Detected local Geyser plugin directory: " + root);
        }
        if (!autoPublishLocal) {
            return new Destination(null, "export only",
                    "bedrock.auto_publish_local is false and bedrock.geyser_data_directory is blank.");
        }
        return new Destination(null, "export only",
                "No local Geyser-Spigot plugin found. If Geyser runs on a proxy or separate "
                + "container, mount its data directory and configure bedrock.geyser_data_directory.");
    }

    public synchronized void publish(BedrockPackCompiler.Result result, Path geyserRoot) throws IOException {
        if (!Files.isDirectory(geyserRoot)) throw new IOException("Geyser data directory does not exist: " + geyserRoot);
        Path pack = geyserRoot.resolve("packs/voxelcore.mcpack");
        Path mappings = geyserRoot.resolve("custom_mappings/voxelcore-items.json");
        Files.createDirectories(pack.getParent()); Files.createDirectories(mappings.getParent());
        byte[] previousPack = Files.exists(pack) ? Files.readAllBytes(pack) : null;
        byte[] previousMappings = Files.exists(mappings) ? Files.readAllBytes(mappings) : null;
        try {
            replace(pack, Files.readAllBytes(result.directory.resolve("voxelcore.mcpack")));
            replace(mappings, Files.readAllBytes(result.directory.resolve("voxelcore-items.json")));
        } catch (IOException failure) {
            try { restore(pack, previousPack); restore(mappings, previousMappings); }
            catch (IOException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }
    private static void restore(Path path, byte[] bytes) throws IOException {
        if (bytes == null) Files.deleteIfExists(path); else replace(path, bytes);
    }
    private static void replace(Path path, byte[] bytes) throws IOException {
        Path stage = Files.createTempFile(path.getParent(), ".voxelcore-", ".tmp");
        try {
            Files.write(stage, bytes);
            try { Files.move(stage, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(stage, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(stage); }
    }
}
