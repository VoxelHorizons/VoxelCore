package org.voxelhorizons.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Stages VoxelCore-owned artifacts for Geyser, then writes a verifiable revision
 * record last. A stopped Geyser is required for registry changes: filesystem
 * publication is NOT atomic with a running Geyser's registry snapshots.
 */
public final class GeyserPackPublisher {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PACK = "packs/voxelcore.mcpack";
    private static final String MAPPINGS = "custom_mappings/voxelcore-items.json";
    private static final String RECEIPT = "voxelcore-publication.json";
    private static final String LOCK = ".voxelcore-publish.lock";

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

    /** The receipt is a detection mechanism, not a guarantee that a running Geyser reloaded the files. */
    public static final class PublicationState {
        public final String status;
        public final String revision;
        public final String detail;
        PublicationState(String status, String revision, String detail) {
            this.status = status;
            this.revision = revision;
            this.detail = detail;
        }
        @Override public String toString() { return status + (revision.isEmpty() ? "" : " revision=" + revision) + ": " + detail; }
    }

    /**
     * Intentionally detectable when someone manually restores only one artifact.
     * A missing receipt is "UNTRACKED", not false confidence that the pair matches.
     */
    public static PublicationState inspect(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root))
            return new PublicationState("MISSING_DIRECTORY", "", String.valueOf(root));
        Path pack = root.resolve(PACK), mappings = root.resolve(MAPPINGS), receipt = root.resolve(RECEIPT);
        if (Files.isSymbolicLink(pack) || Files.isSymbolicLink(mappings) || Files.isSymbolicLink(receipt))
            return new PublicationState("UNSAFE_SYMLINK", "", "VoxelCore publication paths cannot be symlinks");
        if (!Files.isRegularFile(receipt)) {
            String status = Files.exists(pack) || Files.exists(mappings) ? "UNTRACKED" : "NOT_PUBLISHED";
            return new PublicationState(status, "", "No VoxelCore publication receipt");
        }
        JsonObject record;
        try {
            record = JsonParser.parseString(new String(Files.readAllBytes(receipt), StandardCharsets.UTF_8)).getAsJsonObject();
            if (record.get("schema").getAsInt() != 1) throw new IllegalArgumentException("Unexpected receipt schema");
            String revision = record.get("revision").getAsString();
            String expectedPack = record.get("pack_sha256").getAsString();
            String expectedMappings = record.get("mappings_sha256").getAsString();
            if (!validSha256(revision) || !validSha256(expectedPack) || !validSha256(expectedMappings))
                throw new IllegalArgumentException("Invalid receipt digest");
            if (!Files.isRegularFile(pack) || !Files.isRegularFile(mappings))
                return new PublicationState("MISMATCH", revision, "Pack and mappings must both be present");
            if (!expectedPack.equals(digest(Files.readAllBytes(pack)))
                    || !expectedMappings.equals(digest(Files.readAllBytes(mappings))))
                return new PublicationState("MISMATCH", revision, "Pack/mappings SHA-256 differs from receipt");
            return new PublicationState("VERIFIED_ON_DISK", revision,
                    "Both artifacts match the receipt. Restart Geyser and reconnect Bedrock to activate.");
        } catch (RuntimeException invalid) {
            return new PublicationState("CORRUPT_RECEIPT", "", invalid.getMessage());
        }
    }

    @FunctionalInterface
    interface InstallHook { void beforeInstall(int step) throws IOException; }

    private final InstallHook hook;
    public GeyserPackPublisher() { this(step -> {}); }
    GeyserPackPublisher(InstallHook hook) { this.hook = hook; }

    public synchronized void publish(BedrockPackCompiler.Result result, Path geyserRoot) throws IOException {
        if (result == null || result.directory == null || !validSha256(result.revision))
            throw new IOException("Invalid Bedrock build revision/result");
        if (geyserRoot == null || !Files.isDirectory(geyserRoot) || Files.isSymbolicLink(geyserRoot))
            throw new IOException("Geyser data directory does not exist or is unsafe: " + geyserRoot);

        Path pack = geyserRoot.resolve(PACK);
        Path mappings = geyserRoot.resolve(MAPPINGS);
        Path receipt = geyserRoot.resolve(RECEIPT);
        Path lock = geyserRoot.resolve(LOCK);
        for (Path target : new Path[]{pack, mappings, receipt, lock}) {
            if (Files.isSymbolicLink(target)) throw new IOException("Geyser publication target is a symlink: " + target);
        }

        // Validate the pair BEFORE touching the installation.
        byte[] nextPack = Files.readAllBytes(result.directory.resolve("voxelcore.mcpack"));
        byte[] nextMappings = Files.readAllBytes(result.directory.resolve("voxelcore-items.json"));
        verifySource(result.revision, nextPack, nextMappings);
        JsonObject record = new JsonObject();
        record.addProperty("schema", 1);
        record.addProperty("revision", result.revision);
        record.addProperty("pack_sha256", digest(nextPack));
        record.addProperty("mappings_sha256", digest(nextMappings));
        byte[] nextReceipt = JSON.toJson(record).getBytes(StandardCharsets.UTF_8);

        Files.createDirectories(pack.getParent());
        Files.createDirectories(mappings.getParent());
        try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock hold;
            try { hold = channel.tryLock(); }
            catch (OverlappingFileLockException alreadyLocked) {
                throw new IOException("Another Bedrock publication holds " + lock, alreadyLocked);
            }
            if (hold == null) throw new IOException("Another Bedrock publication holds " + lock);
            try (FileLock acquired = hold) {
                for (Path target : new Path[]{pack, mappings, receipt}) {
                    if (Files.isSymbolicLink(target)) throw new IOException("Geyser publication target changed to symlink: " + target);
                }
                byte[] oldPack = readOrNull(pack);
                byte[] oldMappings = readOrNull(mappings);
                byte[] oldReceipt = readOrNull(receipt);
                try {
                    hook.beforeInstall(1); replace(pack, nextPack);
                    hook.beforeInstall(2); replace(mappings, nextMappings);
                    hook.beforeInstall(3); replace(receipt, nextReceipt);
                    PublicationState state = inspect(geyserRoot);
                    if (!"VERIFIED_ON_DISK".equals(state.status))
                        throw new IOException("Published Bedrock pack pair failed verification: " + state);
                } catch (IOException failure) {
                    restoreWithSuppression(pack, oldPack, failure);
                    restoreWithSuppression(mappings, oldMappings, failure);
                    restoreWithSuppression(receipt, oldReceipt, failure);
                    throw failure;
                }
            }
        }
    }

    private static void verifySource(String revision, byte[] archive, byte[] mapping) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(new String(mapping, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("format_version").getAsInt() != 2 || !root.get("items").isJsonObject())
                throw new IOException("Unsupported Geyser custom item mapping format");
        } catch (RuntimeException failure) {
            throw new IOException("Invalid Geyser custom mappings JSON", failure);
        }
        // Inspect actual ZIP rather than relying on the output directory naming.
        Path temporary = Files.createTempFile("voxelcore-validate-", ".mcpack");
        try {
            Files.write(temporary, archive);
            try (ZipFile zip = new ZipFile(temporary.toFile())) {
                ZipEntry manifest = zip.getEntry("manifest.json");
                if (manifest == null) throw new IOException("Bedrock pack missing manifest.json");
                JsonObject root;
                try (InputStream stream = zip.getInputStream(manifest)) {
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    byte[] block = new byte[4096]; int read;
                    while ((read = stream.read(block)) != -1) bytes.write(block, 0, read);
                    root = JsonParser.parseString(new String(bytes.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
                }
                String description = root.getAsJsonObject("header").get("description").getAsString();
                if (!description.contains(revision.substring(0, 12)))
                    throw new IOException("Bedrock pack manifest revision does not match mappings output revision " + revision);
            }
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid Bedrock pack manifest", invalid);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static boolean validSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }
    private static String digest(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder();
            for (byte b : hash) result.append(String.format(Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static byte[] readOrNull(Path path) throws IOException {
        return Files.isRegularFile(path) ? Files.readAllBytes(path) : null;
    }
    private static void restoreWithSuppression(Path path, byte[] bytes, IOException failure) {
        try { if (bytes == null) Files.deleteIfExists(path); else replace(path, bytes); }
        catch (IOException rollback) { failure.addSuppressed(rollback); }
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
