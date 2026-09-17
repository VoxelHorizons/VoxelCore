package org.voxelhorizons.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JavaPackAuthoredAssetsTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void copiesAllAuthoredAssetsIncludingVanillaOverridesAndUnreferencedFiles() throws Exception {
        File contentRoot = temporaryFolder.newFolder("content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());

        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        write(new File(pack, "assets/voxel/textures/misc/unreferenced.png"), "voxel-unreferenced");
        write(new File(pack, "assets/minecraft/textures/block/stone.png"), "vanilla-override");
        write(new File(pack, "assets/example/textures/gui/freeform.png"), "other-namespace");

        Path build = temporaryFolder.newFolder("build").toPath();
        Path output = build.resolve("pack.zip");
        JavaPackBuildResult result = new JavaPackCompiler().compile(
                contentRoot.toPath(), output, build.resolve("render-allocations.yml"),
                JavaPackTarget.numericCmd("asset-copy-test", 22));

        assertEquals(3, result.copiedAssets());
        assertEquals("voxel-unreferenced", zipText(output, "assets/voxel/textures/misc/unreferenced.png"));
        assertEquals("vanilla-override", zipText(output, "assets/minecraft/textures/block/stone.png"));
        assertEquals("other-namespace", zipText(output, "assets/example/textures/gui/freeform.png"));
    }

    @Test
    public void validationAcceptsAssetsOutsideTheContentPackNamespace() throws Exception {
        File contentRoot = temporaryFolder.newFolder("validate-content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());

        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        write(new File(pack, "assets/minecraft/textures/gui/container/generic_54.png"), "gui-override");

        Path build = temporaryFolder.newFolder("validate-build").toPath();
        JavaPackBuildResult result = new JavaPackCompiler().validate(
                contentRoot.toPath(), build.resolve("render-allocations.yml"),
                JavaPackTarget.numericCmd("asset-validation-test", 22));

        assertEquals(1, result.copiedAssets());
    }

    @Test
    public void mergesAuthoredDefaultFontsAndCopiesLanguageOverrides() throws Exception {
        File contentRoot = temporaryFolder.newFolder("font-content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        write(new File(pack, "assets/minecraft/font/default.json"),
                "{\"providers\":[{\"type\":\"reference\",\"id\":\"voxel:branding\"}]}\n");
        write(new File(pack, "assets/voxel/font/branding.json"),
                "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"voxel:brand/logo.png\","
                        + "\"height\":64,\"ascent\":48,\"chars\":[\"\\uEF00\"]}]}\n");
        write(new File(pack, "assets/minecraft/lang/en_nz.json"),
                "{\"menu.game\":\"\\uEF00\",\"menu.returnToGame\":\"Back to VoxelHorizons\"}\n");
        write(new File(pack, "assets/voxel/textures/brand/logo.png"), "logo-bytes");

        Path build = temporaryFolder.newFolder("font-build").toPath();
        Path output = build.resolve("pack.zip");
        new JavaPackCompiler().compile(contentRoot.toPath(), output,
                build.resolve("render-allocations.yml"), JavaPackTarget.MC_1_19_4);

        String defaultFont = zipText(output, "assets/minecraft/font/default.json");
        assertTrue(defaultFont.contains("\"type\":\"reference\""));
        assertTrue(defaultFont.contains("\"id\":\"voxel:branding\""));
        assertTrue(defaultFont.contains("\"type\":\"space\""));
        assertTrue(zipText(output, "assets/voxel/font/branding.json").contains("\\uEF00"));
        assertTrue(zipText(output, "assets/minecraft/lang/en_nz.json").contains("\\uEF00"));
    }

    @Test
    public void rejectsUiSymbolsReservedByAuthoredFonts() throws Exception {
        File contentRoot = temporaryFolder.newFolder("font-collision-content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        write(new File(pack, "assets/voxel/font/branding.json"),
                "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"voxel:brand/logo.png\","
                        + "\"height\":8,\"ascent\":8,\"chars\":[\"\\uEF00\"]}]}\n");
        write(new File(pack, "content/ui.yml"),
                "ui:\n  logo:\n    path: ui/logo.png\n    symbol: '\uEF00'\n");
        File uiTexture = new File(pack, "assets/voxel/textures/ui/logo.png");
        File parent = uiTexture.getParentFile();
        if (!parent.exists()) assertTrue(parent.mkdirs());
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFFFFFF);
        assertTrue(ImageIO.write(image, "png", uiTexture));

        Path build = temporaryFolder.newFolder("font-collision-build").toPath();
        try {
            new JavaPackCompiler().validate(contentRoot.toPath(), build.resolve("render-allocations.yml"),
                    JavaPackTarget.MC_1_19_4);
            fail("Expected authored font symbol collision to fail validation");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("U+EF00"));
            assertTrue(expected.getMessage().contains("authored font"));
        }
    }

    @Test
    public void rebuildRemovesAssetsThatNoLongerExist() throws Exception {
        File contentRoot = temporaryFolder.newFolder("rebuild-content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");

        File obsolete = new File(pack, "assets/voxel/textures/item/obsolete.png");
        write(obsolete, "old-texture");

        Path build = temporaryFolder.newFolder("rebuild-build").toPath();
        Path output = build.resolve("pack.zip");
        Path allocations = build.resolve("render-allocations.yml");
        JavaPackCompiler compiler = new JavaPackCompiler();
        JavaPackTarget target = JavaPackTarget.numericCmd("fresh-rebuild-test", 22);

        compiler.compile(contentRoot.toPath(), output, allocations, target);
        assertTrue(hasEntry(output, "assets/voxel/textures/item/obsolete.png"));

        assertTrue(obsolete.delete());
        write(new File(pack, "assets/voxel/textures/item/current.png"), "new-texture");

        compiler.compile(contentRoot.toPath(), output, allocations, target);
        assertTrue(!hasEntry(output, "assets/voxel/textures/item/obsolete.png"));
        assertEquals("new-texture", zipText(output, "assets/voxel/textures/item/current.png"));
    }

    @Test
    public void failedRebuildPreservesLastPublishedPack() throws Exception {
        File contentRoot = temporaryFolder.newFolder("failed-rebuild-content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        write(new File(pack, "assets/voxel/textures/item/current.png"), "published-texture");

        Path build = temporaryFolder.newFolder("failed-rebuild-build").toPath();
        Path output = build.resolve("pack.zip");
        Path allocations = build.resolve("render-allocations.yml");
        JavaPackCompiler compiler = new JavaPackCompiler();
        JavaPackTarget target = JavaPackTarget.numericCmd("atomic-publish-test", 22);

        compiler.compile(contentRoot.toPath(), output, allocations, target);
        byte[] published = Files.readAllBytes(output);

        write(new File(pack, "content/broken.yml"), "items:\n  broken: [not-a-definition]\n");
        try {
            compiler.compile(contentRoot.toPath(), output, allocations, target);
            fail("Expected invalid content to fail the rebuild");
        } catch (RuntimeException expected) {
            // The failed candidate must not replace the previously published resource pack.
        }

        assertArrayEquals(published, Files.readAllBytes(output));
        assertEquals("published-texture", zipText(output, "assets/voxel/textures/item/current.png"));
    }

    private static void write(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) assertTrue(parent.mkdirs());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean hasEntry(Path zip, String name) throws Exception {
        ZipFile file = new ZipFile(zip.toFile());
        try {
            return file.getEntry(name) != null;
        } finally {
            file.close();
        }
    }

    private static String zipText(Path zip, String name) throws Exception {
        ZipFile file = new ZipFile(zip.toFile());
        try {
            ZipEntry entry = file.getEntry(name);
            if (entry == null) throw new AssertionError("Missing zip entry " + name);
            InputStream input = file.getInputStream(entry);
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int read;
                while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            } finally {
                input.close();
            }
        } finally {
            file.close();
        }
    }
}
