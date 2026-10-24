package org.voxelhorizons.geyser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * B17: pulls a VoxelCore Bedrock publication from a remote Paper server for a Geyser that
 * runs on a proxy or another host. The Paper side signs {@code remote.json} with a shared
 * secret (HMAC-SHA256 over revision, both SHA-256 digests and issue time); this side
 * verifies the signature and both digests, refuses an older issue time than the last
 * installed one (rollback/replay), installs pack, mappings and the standard receipt with
 * rollback on failure, and confirms the result with {@link PublicationVerifier}. It runs
 * at Geyser pre-initialisation, before registries read packs and custom mappings.
 */
final class RemotePublicationSync {
    static final String PROTOCOL = "voxelcore-bedrock-remote|1|";
    static final String PULL_TOKEN_MESSAGE = "voxelcore-bedrock-pull|1";
    static final long MAX_DESCRIPTOR_BYTES = 64 * 1024;
    static final long MAX_PACK_BYTES = 512L * 1024 * 1024;
    static final long MAX_MAPPINGS_BYTES = 64L * 1024 * 1024;
    /** Tolerated clock skew for issue times in the future. */
    static final long MAX_FUTURE_SKEW_MILLIS = 5 * 60_000L;

    enum Outcome { INSTALLED, UP_TO_DATE }

    interface Fetcher {
        byte[] get(String name, long maxBytes) throws IOException;
    }

    private RemotePublicationSync() {}

    static Outcome sync(Path geyserRoot, Path stateFile, byte[] secret, Fetcher fetcher, long now) throws IOException {
        if (secret == null || secret.length < 32) throw new IOException("Remote sync secret must be at least 32 bytes");
        JsonObject descriptor;
        try {
            descriptor = JsonParser.parseString(new String(fetcher.get("remote.json", MAX_DESCRIPTOR_BYTES),
                    StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException invalid) {
            throw new IOException("Remote descriptor is not valid JSON", invalid);
        }
        final String revision, packHash, mappingsHash, signature;
        final long issuedAt;
        try {
            if (descriptor.get("schema").getAsInt() != 1) throw new IOException("Unsupported remote descriptor schema");
            revision = descriptor.get("revision").getAsString();
            packHash = descriptor.get("pack_sha256").getAsString();
            mappingsHash = descriptor.get("mappings_sha256").getAsString();
            issuedAt = descriptor.get("issued_at").getAsLong();
            signature = descriptor.get("signature").getAsString();
        } catch (RuntimeException invalid) {
            throw new IOException("Remote descriptor is missing required fields", invalid);
        }
        if (!sha256Hex(revision) || !sha256Hex(packHash) || !sha256Hex(mappingsHash) || !sha256Hex(signature))
            throw new IOException("Remote descriptor has malformed digests");
        byte[] expected = hmac(secret, PROTOCOL + revision + "|" + packHash + "|" + mappingsHash + "|" + issuedAt);
        if (!MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature)))
            throw new IOException("Remote descriptor signature is invalid");
        if (issuedAt > now + MAX_FUTURE_SKEW_MILLIS)
            throw new IOException("Remote descriptor issue time is in the future");

        State last = State.read(stateFile);
        PublicationVerifier.State onDisk = PublicationVerifier.inspect(geyserRoot);
        if (onDisk.status() == PublicationVerifier.Status.VERIFIED_ON_DISK && revision.equals(onDisk.revision()))
            return Outcome.UP_TO_DATE;
        if (last != null && issuedAt < last.issuedAt)
            throw new IOException("Refusing remote revision " + revision.substring(0, 12)
                    + ": older than installed publication (rollback/replay)");

        byte[] pack = fetcher.get("voxelcore.mcpack", MAX_PACK_BYTES);
        byte[] mappings = fetcher.get("voxelcore-items.json", MAX_MAPPINGS_BYTES);
        if (!packHash.equals(sha256(pack)) || !mappingsHash.equals(sha256(mappings)))
            throw new IOException("Downloaded pack/mappings do not match the signed digests");

        JsonObject receipt = new JsonObject();
        receipt.addProperty("schema", 1);
        receipt.addProperty("revision", revision);
        receipt.addProperty("pack_sha256", packHash);
        receipt.addProperty("mappings_sha256", mappingsHash);
        install(geyserRoot, pack, mappings, receipt.toString().getBytes(StandardCharsets.UTF_8));
        PublicationVerifier.State installed = PublicationVerifier.inspect(geyserRoot);
        if (installed.status() != PublicationVerifier.Status.VERIFIED_ON_DISK || !revision.equals(installed.revision()))
            throw new IOException("Installed remote publication failed verification: " + installed.status());
        new State(revision, issuedAt).write(stateFile);
        return Outcome.INSTALLED;
    }

    /** Bearer token proving knowledge of the secret without sending it. */
    static String pullToken(byte[] secret) throws IOException {
        return HexFormat.of().formatHex(hmac(secret, PULL_TOKEN_MESSAGE));
    }

    private static void install(Path root, byte[] pack, byte[] mappings, byte[] receipt) throws IOException {
        Path[] targets = {root.resolve("packs/voxelcore.mcpack"), root.resolve("custom_mappings/voxelcore-items.json"),
                root.resolve("voxelcore-publication.json")};
        byte[][] next = {pack, mappings, receipt};
        byte[][] previous = new byte[3][];
        for (int i = 0; i < 3; i++) {
            if (Files.isSymbolicLink(targets[i])) throw new IOException("Publication target is a symlink: " + targets[i]);
            previous[i] = Files.isRegularFile(targets[i], LinkOption.NOFOLLOW_LINKS) ? Files.readAllBytes(targets[i]) : null;
        }
        int written = 0;
        try {
            for (; written < 3; written++) replace(targets[written], next[written]);
        } catch (IOException failure) {
            for (int i = 0; i <= Math.min(written, 2); i++) {
                try {
                    if (previous[i] == null) Files.deleteIfExists(targets[i]);
                    else replace(targets[i], previous[i]);
                } catch (IOException rollback) {
                    failure.addSuppressed(rollback);
                }
            }
            throw failure;
        }
    }

    private static void replace(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
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

    static byte[] hmac(byte[] secret, String message) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException impossible) {
            throw new IOException("HmacSHA256 unavailable", impossible);
        }
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (GeneralSecurityException impossible) {
            throw new IOException("SHA-256 unavailable", impossible);
        }
    }

    private static boolean sha256Hex(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    /** Last installed remote publication; guards against rollback to an older signed bundle. */
    record State(String revision, long issuedAt) {
        static State read(Path file) throws IOException {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return null;
            try {
                JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                return new State(json.get("revision").getAsString(), json.get("issued_at").getAsLong());
            } catch (RuntimeException corrupt) {
                throw new IOException("Corrupt remote sync state " + file, corrupt);
            }
        }

        void write(Path file) throws IOException {
            JsonObject json = new JsonObject();
            json.addProperty("revision", revision);
            json.addProperty("issued_at", issuedAt);
            replace(file, json.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** HTTPS (or loopback/LAN HTTP) fetcher with a bounded body size and bearer token. */
    static Fetcher http(URI base, String token) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        String prefix = base.toString().endsWith("/") ? base.toString() : base + "/";
        return (name, maxBytes) -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(prefix + name))
                    .timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + token).GET().build();
            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    if (response.statusCode() != 200)
                        throw new IOException("Remote " + name + " returned HTTP " + response.statusCode());
                    byte[] bytes = body.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
                    if (bytes.length > maxBytes) throw new IOException("Remote " + name + " exceeds " + maxBytes + " bytes");
                    return bytes;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted fetching " + name, interrupted);
            }
        };
    }
}
