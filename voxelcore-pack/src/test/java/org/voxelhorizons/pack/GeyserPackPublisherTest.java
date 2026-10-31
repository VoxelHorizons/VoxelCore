package org.voxelhorizons.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.*;

/** Test both halves of a Geyser deployment, not just successful copying. */
public final class GeyserPackPublisherTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private Path fixture() throws Exception {
        Path root = temp.newFolder().toPath();
        write(root.resolve("demo/pack.yml"), "schema: 1\nnamespace: demo\n");
        write(root.resolve("demo/content/items.yml"),
                "items:\n  token:\n    material: minecraft:paper\n    render:\n      model: demo:item/token\n    properties:\n      bedrock:\n        texture: demo:item/token\n");
        Path texture = root.resolve("demo/assets/demo/textures/item/token.png");
        Files.createDirectories(texture.getParent());
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(16,16,2), "png", texture.toFile());
        return root;
    }

    @Test public void writesVerifiedRevisionReceiptAndDetectsTampering() throws Exception {
        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(fixture(), temp.newFolder().toPath());
        Path geyser = temp.newFolder("Geyser-Spigot").toPath();
        assertEquals("NOT_PUBLISHED", GeyserPackPublisher.inspect(geyser).status);
        new GeyserPackPublisher().publish(result, geyser);
        GeyserPackPublisher.PublicationState state = GeyserPackPublisher.inspect(geyser);
        assertEquals("VERIFIED_ON_DISK", state.status);
        assertEquals(result.revision, state.revision);
        assertTrue(Files.isRegularFile(geyser.resolve("voxelcore-publication.json")));
        assertTrue(Files.isRegularFile(geyser.resolve("packs/voxelcore.mcpack")));
        assertTrue(Files.isRegularFile(geyser.resolve("custom_mappings/voxelcore-items.json")));
        assertTrue(Files.isRegularFile(geyser.resolve("custom_mappings/voxelcore-blocks.json")));
        Files.write(geyser.resolve("custom_mappings/voxelcore-items.json"), "{}".getBytes(StandardCharsets.UTF_8));
        assertEquals("MISMATCH", GeyserPackPublisher.inspect(geyser).status);
    }

    @Test public void rollsBackPreviousPairIfMappingInstallFails() throws Exception {
        Path root = fixture(), output = temp.newFolder().toPath(), geyser = temp.newFolder().toPath();
        BedrockPackCompiler compiler = new BedrockPackCompiler();
        BedrockPackCompiler.Result first = compiler.compile(root, output);
        new GeyserPackPublisher().publish(first, geyser);
        byte[] oldPack = Files.readAllBytes(geyser.resolve("packs/voxelcore.mcpack"));
        byte[] oldMapping = Files.readAllBytes(geyser.resolve("custom_mappings/voxelcore-items.json"));
        byte[] oldBlocks = Files.readAllBytes(geyser.resolve("custom_mappings/voxelcore-blocks.json"));
        byte[] oldReceipt = Files.readAllBytes(geyser.resolve("voxelcore-publication.json"));
        Path texture = root.resolve("demo/assets/demo/textures/item/token.png");
        java.awt.image.BufferedImage changed = new java.awt.image.BufferedImage(16,16,2);
        changed.setRGB(0,0,0xffff1234);
        javax.imageio.ImageIO.write(changed, "png", texture.toFile());
        BedrockPackCompiler.Result second = compiler.compile(root, output);
        assertNotEquals(first.revision, second.revision);
        GeyserPackPublisher failing = new GeyserPackPublisher(step -> {
            if (step == 2) throw new IOException("simulated mappings installation failure");
        });
        try {
            failing.publish(second, geyser);
            fail("Deployment must fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("simulated"));
        }
        assertArrayEquals(oldPack, Files.readAllBytes(geyser.resolve("packs/voxelcore.mcpack")));
        assertArrayEquals(oldMapping, Files.readAllBytes(geyser.resolve("custom_mappings/voxelcore-items.json")));
        assertArrayEquals(oldBlocks, Files.readAllBytes(geyser.resolve("custom_mappings/voxelcore-blocks.json")));
        assertArrayEquals(oldReceipt, Files.readAllBytes(geyser.resolve("voxelcore-publication.json")));
        assertEquals("VERIFIED_ON_DISK", GeyserPackPublisher.inspect(geyser).status);
        assertEquals(first.revision, GeyserPackPublisher.inspect(geyser).revision);
        new GeyserPackPublisher().publish(second, geyser);
        assertEquals(second.revision, GeyserPackPublisher.inspect(geyser).revision);
    }

    @Test public void removesPartiallyPublishedFilesIfThereWasNoPriorPair() throws Exception {
        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(fixture(), temp.newFolder().toPath());
        Path geyser = temp.newFolder().toPath();
        try {
            new GeyserPackPublisher(step -> {
                if (step == 4) throw new IOException("receipt write failed");
            }).publish(result, geyser);
            fail("Deployment must fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("receipt"));
        }
        assertFalse(Files.exists(geyser.resolve("packs/voxelcore.mcpack")));
        assertFalse(Files.exists(geyser.resolve("custom_mappings/voxelcore-items.json")));
        assertFalse(Files.exists(geyser.resolve("custom_mappings/voxelcore-blocks.json")));
        assertFalse(Files.exists(geyser.resolve("voxelcore-publication.json")));
    }

    @Test public void corruptSourceIsRejectedBeforeOverwritingKnownGoodRevision() throws Exception {
        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(fixture(), temp.newFolder().toPath());
        Path geyser = temp.newFolder().toPath();
        new GeyserPackPublisher().publish(result, geyser);
        byte[] original = Files.readAllBytes(geyser.resolve("packs/voxelcore.mcpack"));
        byte[] actual = Files.readAllBytes(result.directory.resolve("voxelcore.mcpack"));
        Files.write(result.directory.resolve("voxelcore.mcpack"), Arrays.copyOf(actual, 25));
        try { new GeyserPackPublisher().publish(result, geyser); fail("Corrupt ZIP must fail"); }
        catch (IOException expected) { /* Validated before copying files. */ }
        assertArrayEquals(original, Files.readAllBytes(geyser.resolve("packs/voxelcore.mcpack")));
        assertEquals("VERIFIED_ON_DISK", GeyserPackPublisher.inspect(geyser).status);
    }

    @Test public void legacyPairWithoutReceiptIsUntrackedRatherThanVerified() throws Exception {
        Path geyser = temp.newFolder().toPath();
        write(geyser.resolve("packs/voxelcore.mcpack"), "old");
        assertEquals("UNTRACKED", GeyserPackPublisher.inspect(geyser).status);
        write(geyser.resolve("voxelcore-publication.json"), "{not json");
        assertEquals("CORRUPT_RECEIPT", GeyserPackPublisher.inspect(geyser).status);
    }

    private static void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }
}
