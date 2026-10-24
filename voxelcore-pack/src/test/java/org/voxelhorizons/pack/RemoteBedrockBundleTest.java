package org.voxelhorizons.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.Assert.*;

public final class RemoteBedrockBundleTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    // Shared vectors: RemotePublicationSyncTest (Geyser extension) asserts the same values.
    static final String PULL_TOKEN_VECTOR = "24336555692103e27e0cf955be34ed0e2df6c5ba59ca07c609fd662bddb48bb2";
    static final String SIGNATURE_VECTOR = "5358605609bff6471d6ccb4b4037d12e5009c38d36f89e2ea1da1871c74ca9ab";

    @Test public void protocolMatchesSharedVectors() throws Exception {
        assertEquals(PULL_TOKEN_VECTOR, RemoteBedrockBundle.pullToken(SECRET));
        String a = repeat('a'), b = repeat('b'), c = repeat('c');
        assertEquals(SIGNATURE_VECTOR, RemoteBedrockBundle.hex(RemoteBedrockBundle.hmac(SECRET,
                RemoteBedrockBundle.PROTOCOL + a + "|" + b + "|" + c + "|1700000000000")));
    }

    @Test public void exportsSignedDescriptorMatchingFiles() throws Exception {
        Path build = temp.newFolder("rev").toPath(), export = temp.getRoot().toPath().resolve("remote");
        Files.write(build.resolve("voxelcore.mcpack"), new byte[]{1, 2, 3});
        Files.write(build.resolve("voxelcore-items.json"), "{}".getBytes(StandardCharsets.UTF_8));
        String revision = repeat('d');
        BedrockPackCompiler.Result result = new BedrockPackCompiler.Result(build, 1, 0, revision, "", 0, 0, 0);
        RemoteBedrockBundle.export(result, SECRET, export, 42L);
        JsonObject descriptor = JsonParser.parseString(new String(Files.readAllBytes(export.resolve("remote.json")),
                StandardCharsets.UTF_8)).getAsJsonObject();
        String packHash = RemoteBedrockBundle.hex(MessageDigest.getInstance("SHA-256").digest(new byte[]{1, 2, 3}));
        assertEquals(packHash, descriptor.get("pack_sha256").getAsString());
        assertEquals(42L, descriptor.get("issued_at").getAsLong());
        assertEquals(RemoteBedrockBundle.hex(RemoteBedrockBundle.hmac(SECRET, RemoteBedrockBundle.PROTOCOL + revision + "|"
                + packHash + "|" + descriptor.get("mappings_sha256").getAsString() + "|42")),
                descriptor.get("signature").getAsString());
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(export.resolve("voxelcore.mcpack")));
    }

    @Test public void authorizesOnlyTheDerivedBearerTokenAndLongSecrets() throws Exception {
        assertTrue(RemoteBedrockBundle.authorized("Bearer " + PULL_TOKEN_VECTOR, SECRET));
        assertFalse(RemoteBedrockBundle.authorized("Bearer " + repeat('0'), SECRET));
        assertFalse(RemoteBedrockBundle.authorized(null, SECRET));
        assertFalse(RemoteBedrockBundle.authorized("Basic x", SECRET));
        assertFalse(RemoteBedrockBundle.authorized("Bearer " + PULL_TOKEN_VECTOR, new byte[8]));
        try { RemoteBedrockBundle.requireSecret(new byte[31]); fail("short"); } catch (java.io.IOException expected) {}
    }

    private static String repeat(char c) {
        char[] chars = new char[64];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
    }
}
