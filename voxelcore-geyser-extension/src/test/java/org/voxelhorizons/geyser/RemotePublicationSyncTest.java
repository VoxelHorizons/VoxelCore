package org.voxelhorizons.geyser;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.Assert.*;

public final class RemotePublicationSyncTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static Map<String, byte[]> bundle(String revisionSeed, byte[] pack, byte[] mappings, long issuedAt, byte[] key)
            throws Exception {
        String revision = sha(revisionSeed.getBytes(StandardCharsets.UTF_8));
        JsonObject descriptor = new JsonObject();
        descriptor.addProperty("schema", 1);
        descriptor.addProperty("revision", revision);
        descriptor.addProperty("pack_sha256", sha(pack));
        descriptor.addProperty("mappings_sha256", sha(mappings));
        descriptor.addProperty("issued_at", issuedAt);
        descriptor.addProperty("signature", HexFormat.of().formatHex(RemotePublicationSync.hmac(key,
                RemotePublicationSync.LEGACY_PROTOCOL + revision + "|" + sha(pack) + "|" + sha(mappings) + "|" + issuedAt)));
        Map<String, byte[]> files = new HashMap<>();
        files.put("remote.json", descriptor.toString().getBytes(StandardCharsets.UTF_8));
        files.put("voxelcore.mcpack", pack);
        files.put("voxelcore-items.json", mappings);
        return files;
    }

    /** Version 2 authenticates all three published files, including custom block mappings. */
    private static Map<String, byte[]> bundleV2(String seed, byte[] pack, byte[] items, byte[] blocks,
                                                 long issuedAt, byte[] key) throws Exception {
        String revision = sha(seed.getBytes(StandardCharsets.UTF_8));
        JsonObject descriptor = new JsonObject();
        descriptor.addProperty("schema", 2);
        descriptor.addProperty("revision", revision);
        descriptor.addProperty("pack_sha256", sha(pack));
        descriptor.addProperty("mappings_sha256", sha(items));
        descriptor.addProperty("blocks_sha256", sha(blocks));
        descriptor.addProperty("issued_at", issuedAt);
        descriptor.addProperty("signature", HexFormat.of().formatHex(RemotePublicationSync.hmac(key,
                RemotePublicationSync.PROTOCOL + revision + "|" + sha(pack) + "|" + sha(items)
                        + "|" + sha(blocks) + "|" + issuedAt)));
        Map<String, byte[]> files = new HashMap<>();
        files.put("remote.json", descriptor.toString().getBytes(StandardCharsets.UTF_8));
        files.put("voxelcore.mcpack", pack);
        files.put("voxelcore-items.json", items);
        files.put("voxelcore-blocks.json", blocks);
        return files;
    }

    private static RemotePublicationSync.Fetcher from(Map<String, byte[]> files) {
        return (name, max) -> {
            byte[] bytes = files.get(name);
            if (bytes == null) throw new IOException("missing " + name);
            return bytes;
        };
    }

    @Test public void installsVerifiedPairThenReportsUpToDate() throws Exception {
        Path geyser = temp.newFolder().toPath(), state = geyser.resolve("state.json");
        Map<String, byte[]> files = bundle("r1", new byte[]{1, 2, 3}, "{\"format_version\":2}".getBytes(), 1000, SECRET);
        assertEquals(RemotePublicationSync.Outcome.INSTALLED, RemotePublicationSync.sync(geyser, state, SECRET, from(files), 2000));
        assertEquals(PublicationVerifier.Status.VERIFIED_ON_DISK, PublicationVerifier.inspect(geyser).status());
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(geyser.resolve("packs/voxelcore.mcpack")));
        assertEquals(RemotePublicationSync.Outcome.UP_TO_DATE, RemotePublicationSync.sync(geyser, state, SECRET, from(files), 3000));
    }

    @Test public void installsAndValidatesRemoteCustomBlockMappings() throws Exception {
        Path geyser = temp.newFolder().toPath(), state = geyser.resolve("state.json");
        byte[] blocks = "{\"format_version\":1,\"blocks\":{}}".getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> files = bundleV2("blocks", new byte[]{1, 3}, new byte[]{5}, blocks, 1000, SECRET);
        assertEquals(RemotePublicationSync.Outcome.INSTALLED,
                RemotePublicationSync.sync(geyser, state, SECRET, from(files), 2000));
        assertArrayEquals(blocks, Files.readAllBytes(geyser.resolve("custom_mappings/voxelcore-blocks.json")));
        assertEquals(PublicationVerifier.Status.VERIFIED_ON_DISK, PublicationVerifier.inspect(geyser).status());
        Files.writeString(geyser.resolve("custom_mappings/voxelcore-blocks.json"), "tampered");
        assertEquals(PublicationVerifier.Status.MISMATCH, PublicationVerifier.inspect(geyser).status());
        files.put("voxelcore-blocks.json", new byte[]{9});
        assertRejected(geyser, state, files, 3000, "do not match");
    }

    @Test public void rejectsWrongKeyTamperedArtifactsAndFutureIssueTime() throws Exception {
        Path geyser = temp.newFolder().toPath(), state = geyser.resolve("state.json");
        byte[] wrongKey = "ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8);
        assertRejected(geyser, state, bundle("r1", new byte[]{1}, new byte[]{2}, 1000, wrongKey), 2000, "signature");
        Map<String, byte[]> tampered = bundle("r1", new byte[]{1}, new byte[]{2}, 1000, SECRET);
        tampered.put("voxelcore.mcpack", new byte[]{9});
        assertRejected(geyser, state, tampered, 2000, "do not match");
        assertRejected(geyser, state, bundle("r1", new byte[]{1}, new byte[]{2}, 10_000_000, SECRET), 0, "future");
        assertFalse("Nothing may be installed from a rejected bundle", Files.exists(geyser.resolve("packs/voxelcore.mcpack")));
    }

    @Test public void refusesRollbackToOlderSignedBundleAndKeepsCurrentFiles() throws Exception {
        Path geyser = temp.newFolder().toPath(), state = geyser.resolve("state.json");
        RemotePublicationSync.sync(geyser, state, SECRET, from(bundle("new", new byte[]{7}, new byte[]{8}, 5000, SECRET)), 6000);
        assertRejected(geyser, state, bundle("old", new byte[]{1}, new byte[]{2}, 4000, SECRET), 6000, "rollback");
        assertArrayEquals(new byte[]{7}, Files.readAllBytes(geyser.resolve("packs/voxelcore.mcpack")));
        assertEquals(RemotePublicationSync.Outcome.INSTALLED, RemotePublicationSync.sync(geyser, state, SECRET,
                from(bundle("newer", new byte[]{5}, new byte[]{6}, 7000, SECRET)), 8000));
    }

    @Test public void protocolMatchesPaperProducerVectors() throws Exception {
        // Same vectors as voxelcore-pack RemoteBedrockBundleTest: producer and consumer agree.
        assertEquals("24336555692103e27e0cf955be34ed0e2df6c5ba59ca07c609fd662bddb48bb2",
                RemotePublicationSync.pullToken(SECRET));
        assertEquals("c8d45b2688d3b68b24587e12804c6ce4b1183d21140b4810b8ef754d353a74e9",
                HexFormat.of().formatHex(RemotePublicationSync.hmac(SECRET, RemotePublicationSync.PROTOCOL
                        + "a".repeat(64) + "|" + "b".repeat(64) + "|" + "c".repeat(64) + "|" + "c".repeat(64) + "|1700000000000")));
    }

    @Test public void shortSecretsAreRefused() throws Exception {
        Path geyser = temp.newFolder().toPath();
        try {
            RemotePublicationSync.sync(geyser, geyser.resolve("s"), new byte[8], from(new HashMap<>()), 0);
            fail("short secret");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("32 bytes"));
        }
    }

    @Test public void httpFetcherSendsBearerTokenAndBoundsBodies() throws Exception {
        String token = RemotePublicationSync.pullToken(SECRET);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bedrock/", exchange -> {
            byte[] body = ("Bearer " + token).equals(exchange.getRequestHeaders().getFirst("Authorization"))
                    ? "0123456789".getBytes(StandardCharsets.UTF_8) : new byte[0];
            exchange.sendResponseHeaders(body.length == 0 ? 403 : 200, body.length == 0 ? -1 : body.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/bedrock");
            assertEquals("0123456789", new String(RemotePublicationSync.http(base, token).get("remote.json", 64),
                    StandardCharsets.UTF_8));
            try { RemotePublicationSync.http(base, token).get("remote.json", 4); fail("size bound"); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("exceeds")); }
            try { RemotePublicationSync.http(base, "wrong").get("remote.json", 64); fail("token"); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("403")); }
        } finally {
            server.stop(0);
        }
    }

    private static void assertRejected(Path geyser, Path state, Map<String, byte[]> files, long now, String reason) {
        try {
            RemotePublicationSync.sync(geyser, state, SECRET, from(files), now);
            fail("Expected rejection: " + reason);
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(reason));
        }
    }
}
