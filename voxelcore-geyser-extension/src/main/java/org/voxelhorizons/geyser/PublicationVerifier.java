package org.voxelhorizons.geyser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Read-only VoxelCore publication receipt validation from the *actual* Geyser directory. */
public final class PublicationVerifier {
    public enum Status { NOT_PUBLISHED, UNTRACKED, VERIFIED_ON_DISK, MISMATCH, CORRUPT_RECEIPT, IO_ERROR, UNSAFE_PATH }
    public record State(Status status, String revision, String details) {}

    private PublicationVerifier() {}

    public static State inspect(Path root) {
        if (root == null || !Files.isDirectory(root))
            return new State(Status.NOT_PUBLISHED, "", "Geyser data directory is absent");
        Path pack = root.resolve("packs/voxelcore.mcpack");
        Path mapping = root.resolve("custom_mappings/voxelcore-items.json");
        Path blocks = root.resolve("custom_mappings/voxelcore-blocks.json");
        Path receipt = root.resolve("voxelcore-publication.json");
        try {
            if (Files.isSymbolicLink(pack) || Files.isSymbolicLink(mapping)
                    || Files.isSymbolicLink(blocks) || Files.isSymbolicLink(receipt))
                return new State(Status.UNSAFE_PATH, "", "Publication paths cannot be symlinks");
            if (!Files.isRegularFile(receipt)) {
                if (Files.isRegularFile(pack) || Files.isRegularFile(mapping) || Files.isRegularFile(blocks))
                    return new State(Status.UNTRACKED, "", "Pack/mappings found without revision receipt");
                return new State(Status.NOT_PUBLISHED, "", "No VoxelCore publication files found");
            }
            JsonObject json;
            try {
                json = JsonParser.parseString(Files.readString(receipt, StandardCharsets.UTF_8)).getAsJsonObject();
                if (json.get("schema").getAsInt() != 1)
                    throw new IllegalArgumentException("Unsupported receipt schema");
                String revision = json.get("revision").getAsString();
                String packHash = json.get("pack_sha256").getAsString();
                String mappingHash = json.get("mappings_sha256").getAsString();
                boolean hasBlocks = json.has("blocks_sha256");
                String blocksHash = hasBlocks ? json.get("blocks_sha256").getAsString() : "";
                if (!valid(revision) || !valid(packHash) || !valid(mappingHash)
                        || (hasBlocks && !valid(blocksHash)))
                    throw new IllegalArgumentException("Invalid SHA-256 fields");
                if (!Files.isRegularFile(pack) || !Files.isRegularFile(mapping)
                        || (hasBlocks && !Files.isRegularFile(blocks)))
                    return new State(Status.MISMATCH, revision, "Published files required by receipt are missing");
                if (!packHash.equals(sha256(Files.readAllBytes(pack)))
                        || !mappingHash.equals(sha256(Files.readAllBytes(mapping)))
                        || (hasBlocks && !blocksHash.equals(sha256(Files.readAllBytes(blocks)))))
                    return new State(Status.MISMATCH, revision, "Published file digests do not match the receipt");
                return new State(Status.VERIFIED_ON_DISK, revision, hasBlocks
                        ? "Pack, items and blocks match receipt; active Geyser/client state is not verified"
                        : "Legacy pack/item pair matches receipt; block mappings are not covered");
            } catch (RuntimeException invalid) {
                return new State(Status.CORRUPT_RECEIPT, "", invalid.getMessage());
            }
        } catch (IOException failure) {
            return new State(Status.IO_ERROR, "", failure.getMessage());
        }
    }

    private static boolean valid(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
