package org.voxelhorizons.pack;

import java.io.IOException;
import java.nio.file.*;

/** Installs only VoxelCore-owned files. Geyser must be stopped/restarted for registry changes. */
public final class GeyserPackPublisher {
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
