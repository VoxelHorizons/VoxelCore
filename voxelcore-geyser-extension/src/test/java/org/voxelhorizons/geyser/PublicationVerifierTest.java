package org.voxelhorizons.geyser;

import com.google.gson.JsonObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.Assert.*;

public final class PublicationVerifierTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test public void acceptsExactPublishedPairAndDetectsExternalChanges() throws Exception {
        Path root = temp.newFolder().toPath();
        assertEquals(PublicationVerifier.Status.NOT_PUBLISHED, PublicationVerifier.inspect(root).status());
        Path pack=root.resolve("packs/voxelcore.mcpack");
        Path mapping=root.resolve("custom_mappings/voxelcore-items.json");
        Files.createDirectories(pack.getParent()); Files.createDirectories(mapping.getParent());
        byte[] p=new byte[]{1,2,3}, m="{}".getBytes(StandardCharsets.UTF_8);
        Files.write(pack,p); Files.write(mapping,m);
        assertEquals(PublicationVerifier.Status.UNTRACKED,PublicationVerifier.inspect(root).status());
        String revision = "ab".repeat(32);
        JsonObject record = new JsonObject();
        record.addProperty("schema",1);record.addProperty("revision",revision);
        record.addProperty("pack_sha256",sha(p));
        record.addProperty("mappings_sha256",sha(m));
        Files.writeString(root.resolve("voxelcore-publication.json"),record.toString(),StandardCharsets.UTF_8);
        PublicationVerifier.State ok=PublicationVerifier.inspect(root);
        assertEquals(PublicationVerifier.Status.VERIFIED_ON_DISK,ok.status());
        assertEquals(revision,ok.revision());
        Files.writeString(mapping,"{\"changed\":true}",StandardCharsets.UTF_8);
        assertEquals(PublicationVerifier.Status.MISMATCH,PublicationVerifier.inspect(root).status());
        Files.delete(pack);
        assertEquals(PublicationVerifier.Status.MISMATCH,PublicationVerifier.inspect(root).status());
    }

    @Test public void rejectsCorruptManifestRatherThanAssumingLoaded() throws Exception {
        Path root=temp.newFolder().toPath();
        Files.writeString(root.resolve("voxelcore-publication.json"),"{broken",StandardCharsets.UTF_8);
        assertEquals(PublicationVerifier.Status.CORRUPT_RECEIPT,PublicationVerifier.inspect(root).status());
    }
}
