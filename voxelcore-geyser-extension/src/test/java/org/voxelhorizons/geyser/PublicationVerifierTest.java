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

    @Test public void validatesBlockMappingAndRejectsTampering() throws Exception {
        Path root = temp.newFolder().toPath();
        Path pack = root.resolve("packs/voxelcore.mcpack");
        Path items = root.resolve("custom_mappings/voxelcore-items.json");
        Path blocks = root.resolve("custom_mappings/voxelcore-blocks.json");
        Files.createDirectories(pack.getParent());
        Files.createDirectories(items.getParent());
        byte[] p = {1, 2}, i = {3, 4}, b = "{\"format_version\":1,\"blocks\":{}}".getBytes(StandardCharsets.UTF_8);
        Files.write(pack, p);
        Files.write(items, i);
        Files.write(blocks, b);
        JsonObject receipt = new JsonObject();
        receipt.addProperty("schema", 1);
        receipt.addProperty("revision", "ab".repeat(32));
        receipt.addProperty("pack_sha256", sha(p));
        receipt.addProperty("mappings_sha256", sha(i));
        receipt.addProperty("blocks_sha256", sha(b));
        Files.writeString(root.resolve("voxelcore-publication.json"), receipt.toString());
        assertEquals(PublicationVerifier.Status.VERIFIED_ON_DISK, PublicationVerifier.inspect(root).status());
        Files.writeString(blocks, "{}");
        assertEquals(PublicationVerifier.Status.MISMATCH, PublicationVerifier.inspect(root).status());
    }

    @Test public void rejectsCorruptManifestRatherThanAssumingLoaded() throws Exception {
        Path root=temp.newFolder().toPath();
        Files.writeString(root.resolve("voxelcore-publication.json"),"{broken",StandardCharsets.UTF_8);
        assertEquals(PublicationVerifier.Status.CORRUPT_RECEIPT,PublicationVerifier.inspect(root).status());
    }
}
