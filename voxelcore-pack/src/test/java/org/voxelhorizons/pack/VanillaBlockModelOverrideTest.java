package org.voxelhorizons.pack;

import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Regression coverage for replacing full vanilla block models in content-pack overlays. */
public class VanillaBlockModelOverrideTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void customLeavesReplaceEarlierBlockModelsWithoutMergingParentOrTextures() throws Exception {
        Path root = temporaryFolder.newFolder("content").toPath();
        Path vanilla = root.resolve("vanilla");
        Path custom = root.resolve("custom");
        write(vanilla.resolve("pack.yml"), "schema: 1\nnamespace: minecraft\n");
        write(custom.resolve("pack.yml"), "schema: 1\nnamespace: voxel\n");
        Files.createDirectories(vanilla.resolve("content"));
        Files.createDirectories(custom.resolve("content"));

        write(vanilla.resolve("assets/minecraft/models/block/acacia_leaves.json"),
                "{\"parent\":\"minecraft:block/leaves\",\"textures\":{\"all\":\"minecraft:block/acacia_leaves\"}}\n");
        String acacia = "{\"parent\":\"block/leaves_cross\",\"textures\":{\"all\":\"block/acacia_leaves\","
                + "\"bushy\":\"block/acacia_leaves_bushy\"}}\n";
        write(custom.resolve("assets/minecraft/models/block/acacia_leaves.json"), acacia);

        write(vanilla.resolve("assets/minecraft/models/block/mangrove_leaves.json"),
                "{\"parent\":\"minecraft:block/leaves\",\"textures\":{\"all\":\"minecraft:block/mangrove_leaves\"}}\n");
        String mangrove = "{\"parent\":\"block/leaves_cross_new\",\"textures\":{\"all\":\"block/mangrove_leaves\","
                + "\"bushy_bottom\":\"block/mangrove_leaves_bushy_bottom\","
                + "\"bushy_top\":\"block/mangrove_leaves_bushy_top\"}}\n";
        write(custom.resolve("assets/minecraft/models/block/mangrove_leaves.json"), mangrove);

        // Other JSON files still use the existing additive merge semantics.
        write(vanilla.resolve("assets/minecraft/lang/en_us.json"), "{\"test.original\":\"Original\"}\n");
        write(custom.resolve("assets/minecraft/lang/en_us.json"), "{\"test.custom\":\"Custom\"}\n");

        Path build = temporaryFolder.newFolder("build").toPath();
        Path output = build.resolve("pack.zip");
        new JavaPackCompiler().compile(root, output, build.resolve("render-allocations.yml"), JavaPackTarget.MC_26_3);

        assertEquals(JsonParser.parseString(acacia),
                JsonParser.parseString(zipText(output, "assets/minecraft/models/block/acacia_leaves.json")));
        assertEquals(JsonParser.parseString(mangrove),
                JsonParser.parseString(zipText(output, "assets/minecraft/models/block/mangrove_leaves.json")));
        String language = zipText(output, "assets/minecraft/lang/en_us.json");
        assertTrue(language.contains("\"test.original\":\"Original\""));
        assertTrue(language.contains("\"test.custom\":\"Custom\""));
    }

    @Test
    public void malformedAuthoredBlockModelIsRejectedInsteadOfSilentlyReplacing() throws Exception {
        Path root = temporaryFolder.newFolder("invalid-content").toPath();
        Path vanilla = root.resolve("vanilla");
        Path custom = root.resolve("custom");
        write(vanilla.resolve("pack.yml"), "schema: 1\nnamespace: minecraft\n");
        write(custom.resolve("pack.yml"), "schema: 1\nnamespace: voxel\n");
        Files.createDirectories(vanilla.resolve("content"));
        Files.createDirectories(custom.resolve("content"));
        write(vanilla.resolve("assets/minecraft/models/block/acacia_leaves.json"), "{\"parent\":\"block/leaves\"}\n");
        write(custom.resolve("assets/minecraft/models/block/acacia_leaves.json"),
                "{\"parent\":\"block/leaves_cross\",\"parent\":\"block/block\"}");

        Path build = temporaryFolder.newFolder("invalid-build").toPath();
        try {
            new JavaPackCompiler().compile(root, build.resolve("pack.zip"),
                    build.resolve("render-allocations.yml"), JavaPackTarget.MC_26_3);
            fail("Expected duplicate-key model JSON to fail");
        } catch (JavaPackCompileException expected) {
            assertTrue(expected.getMessage().contains("duplicate key $.parent"));
        }
    }

    private static void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String zipText(Path zip, String path) throws Exception {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            ZipEntry entry = file.getEntry(path);
            if (entry == null) throw new AssertionError("Missing zip entry: " + path);
            try (InputStream input = file.getInputStream(entry)) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }
}
