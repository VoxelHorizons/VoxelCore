package org.voxelhorizons.geyser;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public final class RemoteProjectionFeedsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private static RemoteProjectionFeeds.Response signed(byte[] body, long issuedAt, byte[] key) throws Exception {
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        String signature = HexFormat.of().formatHex(RemotePublicationSync.hmac(key,
                RemoteProjectionFeeds.PROTOCOL + "furniture|" + issuedAt + "|" + digest));
        return new RemoteProjectionFeeds.Response(200, body, signature, Long.toString(issuedAt));
    }

    @Test public void protocolMatchesPaperProducerVector() throws Exception {
        byte[] body = "{\"schema\":1}".getBytes(StandardCharsets.UTF_8);
        // Same vector as voxelcore-pack RemoteBedrockBundleTest#projectionSignatureVector.
        assertEquals("0225a7321e7874e9e2b6a283a752af4d08f529a07b958792a5761ba40ee2d997",
                signed(body, 1700000000000L, SECRET).signature());
    }

    @Test public void mirrorsSignedFeedIntoIndexAndIgnoresReplaysAndForgeries() throws Exception {
        Path geyser = temp.newFolder().toPath();
        UUID display = UUID.randomUUID();
        long now = System.currentTimeMillis();
        byte[] feed = ("{\"schema\":1,\"furniture\":[{\"model_item\":\"voxel:oak_bench\",\"entity_links\":{"
                + "\"display_entity_uuid\":\"" + display + "\",\"parts\":[]}}]}").getBytes(StandardCharsets.UTF_8);
        AtomicReference<RemoteProjectionFeeds.Response> next = new AtomicReference<>(signed(feed, now, SECRET));
        RemoteProjectionFeeds feeds = new RemoteProjectionFeeds(geyser, "survival", SECRET,
                kind -> kind.equals("furniture") ? next.get() : new RemoteProjectionFeeds.Response(404, new byte[0], null, null));
        assertEquals(1, feeds.poll(now));
        Path mirrored = geyser.resolve("voxelcore-projection/furniture-survival.json");
        assertEquals(now, Files.getLastModifiedTime(mirrored).toMillis());
        EntityProjectionIndex index = new EntityProjectionIndex(geyser,
                Map.of("voxel:oak_bench", "voxelcore:entity_i_1234567890abcdef1234567890abcdef"));
        assertNotNull(index.entityType(display));

        assertEquals("Same issue time is a replay", 0, feeds.poll(now + 10));
        next.set(signed("{\"schema\":1,\"furniture\":[]}".getBytes(StandardCharsets.UTF_8), now + 500,
                "ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Forged feed ignored", 0, feeds.poll(now + 600));
        assertNotNull("Previous verified feed kept", index.entityType(display));

        next.set(signed("{\"schema\":1,\"furniture\":[]}".getBytes(StandardCharsets.UTF_8), now + 1000, SECRET));
        assertEquals(1, feeds.poll(now + 1100));
        assertNull("Newer verified feed replaces the old one", index.entityType(display));
    }

    @Test public void rejectsBadBackendIdsAndShortSecrets() {
        Path root = temp.getRoot().toPath();
        try { new RemoteProjectionFeeds(root, "Bad Id", SECRET, kind -> null); fail("id"); }
        catch (IllegalArgumentException expected) { }
        try { new RemoteProjectionFeeds(root, "lobby", new byte[4], kind -> null); fail("secret"); }
        catch (IllegalArgumentException expected) { }
    }
}
