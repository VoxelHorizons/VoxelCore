package org.voxelhorizons.pack;

import com.google.gson.JsonObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * B17 producer: exports the built Bedrock pair with a signed {@code remote.json} for a
 * Geyser running on a proxy or another host (consumer: the Geyser extension's
 * RemotePublicationSync). Signature = HMAC-SHA256(secret,
 * "voxelcore-bedrock-remote|1|revision|pack_sha256|mappings_sha256|issued_at").
 * Pulls authenticate with a bearer token = HMAC-SHA256(secret, "voxelcore-bedrock-pull|1"),
 * so the secret itself never crosses the network.
 */
public final class RemoteBedrockBundle {
    public static final String DESCRIPTOR = "remote.json";
    public static final String PACK = "voxelcore.mcpack";
    public static final String MAPPINGS = "voxelcore-items.json";
    public static final List<String> FILES = Collections.unmodifiableList(Arrays.asList(DESCRIPTOR, PACK, MAPPINGS));
    static final String PROTOCOL = "voxelcore-bedrock-remote|1|";
    static final String PULL_TOKEN_MESSAGE = "voxelcore-bedrock-pull|1";

    private RemoteBedrockBundle() {}

    public static void requireSecret(byte[] secret) throws IOException {
        if (secret == null || secret.length < 32) throw new IOException("bedrock remote secret must be at least 32 bytes");
    }

    /**
     * Copies pack and mappings, then writes the descriptor last, each atomically, so a puller
     * sees either the previous descriptor or one whose digests match files already present.
     */
    public static void export(BedrockPackCompiler.Result result, byte[] secret, Path exportDir, long issuedAt)
            throws IOException {
        requireSecret(secret);
        if (result == null || result.directory == null) throw new IOException("No Bedrock build result to export");
        byte[] pack = Files.readAllBytes(result.directory.resolve(PACK));
        byte[] mappings = Files.readAllBytes(result.directory.resolve(MAPPINGS));
        String packHash = hex(MessageDigestHolder.sha256(pack)), mappingsHash = hex(MessageDigestHolder.sha256(mappings));
        JsonObject descriptor = new JsonObject();
        descriptor.addProperty("schema", 1);
        descriptor.addProperty("revision", result.revision);
        descriptor.addProperty("pack_sha256", packHash);
        descriptor.addProperty("mappings_sha256", mappingsHash);
        descriptor.addProperty("issued_at", issuedAt);
        descriptor.addProperty("signature", hex(hmac(secret,
                PROTOCOL + result.revision + "|" + packHash + "|" + mappingsHash + "|" + issuedAt)));
        if (Files.isSymbolicLink(exportDir)) throw new IOException("Bedrock remote export directory is a symlink");
        Files.createDirectories(exportDir);
        replace(exportDir.resolve(PACK), pack);
        replace(exportDir.resolve(MAPPINGS), mappings);
        replace(exportDir.resolve(DESCRIPTOR), descriptor.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static String pullToken(byte[] secret) throws IOException {
        return hex(hmac(secret, PULL_TOKEN_MESSAGE));
    }

    /** Constant-time check of an {@code Authorization: Bearer <token>} header. */
    public static boolean authorized(String authorization, byte[] secret) {
        if (authorization == null || secret == null || secret.length < 32 || !authorization.startsWith("Bearer ")) return false;
        try {
            byte[] expected = pullToken(secret).getBytes(StandardCharsets.US_ASCII);
            byte[] presented = authorization.substring(7).trim().getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, presented);
        } catch (IOException unavailable) {
            return false;
        }
    }

    static byte[] hmac(byte[] secret, String message) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException impossible) {
            throw new IOException("HmacSHA256 unavailable", impossible);
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }

    private static void replace(Path target, byte[] bytes) throws IOException {
        if (Files.isSymbolicLink(target)) throw new IOException("Bedrock remote export target is a symlink: " + target);
        Path staging = Files.createTempFile(target.getParent(), ".voxelcore-", ".tmp");
        try {
            Files.write(staging, bytes);
            try {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    private static final class MessageDigestHolder {
        static byte[] sha256(byte[] bytes) throws IOException {
            try {
                return MessageDigest.getInstance("SHA-256").digest(bytes);
            } catch (GeneralSecurityException impossible) {
                throw new IOException("SHA-256 unavailable", impossible);
            }
        }
    }
}
