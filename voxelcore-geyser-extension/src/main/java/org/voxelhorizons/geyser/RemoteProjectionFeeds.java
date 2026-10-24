package org.voxelhorizons.geyser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * B17: mirrors live furniture/vehicle projection feeds from remote Paper backends into
 * {@code voxelcore-projection/<kind>-<backend>.json}, so {@link EntityProjectionIndex}
 * merges them exactly like mounted feeds. Each response is signed by the backend's
 * VoxelCore host: HMAC-SHA256(secret, "voxelcore-projection|1|kind|issuedAt|sha256(body)").
 * Unsigned, mis-signed or replayed (not newer) responses are ignored; the local file keeps
 * the backend's issue time as its modification time, so a backend that stops answering
 * expires through the index's normal 60-second freshness rule.
 */
final class RemoteProjectionFeeds {
    static final String PROTOCOL = "voxelcore-projection|1|";
    static final List<String> KINDS = List.of("furniture", "vehicles");
    static final long MAX_FEED_BYTES = 5_000_000L;

    record Response(int status, byte[] body, String signature, String issuedAt) {}

    interface Fetcher {
        Response get(String kind) throws IOException;
    }

    private final Path folder;
    private final String backend;
    private final byte[] secret;
    private final Fetcher fetcher;
    private final Map<String, Long> lastIssued = new HashMap<>();

    RemoteProjectionFeeds(Path geyserRoot, String backend, byte[] secret, Fetcher fetcher) {
        if (!backend.matches("[a-z0-9_-]{1,32}")) throw new IllegalArgumentException("Backend id must match [a-z0-9_-]{1,32}");
        if (secret == null || secret.length < 32) throw new IllegalArgumentException("Secret must be at least 32 bytes");
        this.folder = geyserRoot.resolve("voxelcore-projection");
        this.backend = backend;
        this.secret = secret.clone();
        this.fetcher = fetcher;
    }

    /** Fetch every kind once; returns how many feeds were updated. Never throws for one bad feed. */
    synchronized int poll(long now) {
        int updated = 0;
        for (String kind : KINDS) {
            try {
                if (pollOne(kind, now)) updated++;
            } catch (IOException | RuntimeException rejected) {
                // Keep the previous file; it expires on its own if the backend stays unusable.
            }
        }
        return updated;
    }

    boolean pollOne(String kind, long now) throws IOException {
        Response response = fetcher.get(kind);
        if (response.status() == 404) return false; // Backend does not run this plugin.
        if (response.status() != 200) throw new IOException(kind + " feed HTTP " + response.status());
        if (response.body().length > MAX_FEED_BYTES) throw new IOException(kind + " feed too large");
        long issuedAt;
        try { issuedAt = Long.parseLong(response.issuedAt()); }
        catch (RuntimeException invalid) { throw new IOException(kind + " feed has no issue time"); }
        String signature = response.signature();
        if (signature == null || !signature.matches("[0-9a-f]{64}")) throw new IOException(kind + " feed unsigned");
        byte[] expected = RemotePublicationSync.hmac(secret,
                PROTOCOL + kind + "|" + issuedAt + "|" + sha256(response.body()));
        if (!MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature)))
            throw new IOException(kind + " feed signature invalid");
        if (issuedAt > now + RemotePublicationSync.MAX_FUTURE_SKEW_MILLIS) throw new IOException(kind + " feed from the future");
        Long previous = lastIssued.get(kind);
        if (previous != null && issuedAt <= previous) return false; // Unchanged or replayed.
        Files.createDirectories(folder);
        Path target = folder.resolve(kind + "-" + backend + ".json");
        if (Files.isSymbolicLink(target)) throw new IOException("Projection target is a symlink");
        Path staging = Files.createTempFile(folder, ".remote-", ".tmp");
        try {
            Files.write(staging, response.body());
            // The backend's issue time drives freshness: a silent backend expires normally.
            Files.setLastModifiedTime(staging, FileTime.fromMillis(issuedAt));
            try {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staging);
        }
        lastIssued.put(kind, issuedAt);
        return true;
    }

    static Fetcher http(URI base, String token) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        String prefix = base.toString().endsWith("/") ? base.toString() : base + "/";
        return kind -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(prefix + "projection/" + kind + ".json"))
                    .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + token).GET().build();
            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    byte[] bytes = body.readNBytes((int) MAX_FEED_BYTES + 1);
                    return new Response(response.statusCode(), bytes,
                            response.headers().firstValue("X-VoxelCore-Signature").orElse(null),
                            response.headers().firstValue("X-VoxelCore-Issued-At").orElse(null));
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted fetching " + kind + " feed", interrupted);
            }
        };
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.GeneralSecurityException impossible) {
            throw new IOException("SHA-256 unavailable", impossible);
        }
    }
}
