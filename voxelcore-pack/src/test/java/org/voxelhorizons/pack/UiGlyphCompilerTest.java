package org.voxelhorizons.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.voxelhorizons.content.ContentID;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UiGlyphCompilerTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void compilesCroppedUiWithStableAutomaticGlyphAndSpacing() throws Exception {
        File contentRoot = temporaryFolder.newFolder("content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());
        File texture = new File(pack, "assets/voxel/textures/ui/npc/warps_menu.png");
        assertTrue(texture.getParentFile().mkdirs());
        writePng(texture, 176, 18);
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        write(new File(pack, "content/ui.yml"),
                "ui:\n  warps_menu:\n    path: ui/npc/warps_menu.png\n" +
                "    scale_ratio: 18\n    y_position: 8\n");

        Path build = temporaryFolder.newFolder("build").toPath();
        Path renderAllocations = build.resolve("render-allocations.yml");
        Path output = build.resolve("pack.zip");
        JavaPackCompiler compiler = new JavaPackCompiler();
        compiler.compile(contentRoot.toPath(), output, renderAllocations, JavaPackTarget.MC_1_14_4);

        UiGlyphRegistry registry = compiler.loadUiGlyphs(contentRoot.toPath(),
                build.resolve("glyph-allocations.yml"), false);
        UiGlyphDefinition glyph = registry.get(ContentID.of("voxel", "warps_menu")).get();
        assertEquals(18, glyph.scaleRatio());
        assertEquals(8, glyph.yPosition());
        assertEquals(0xE000, glyph.codePoint());
        assertEquals(glyph.character(), registry.resolveAliases(":warps_menu:"));
        assertEquals(glyph.character(), registry.resolveAliases(":voxel/warps_menu:"));

        String font = zipText(output, "assets/minecraft/font/default.json");
        assertTrue(font.contains("\"type\":\"space\""));
        assertTrue(font.contains("\"type\":\"bitmap\""));
        assertTrue(font.contains("\"file\":\"voxel:ui/npc/warps_menu.png\""));
        assertTrue(font.contains("\"height\":18"));
        assertTrue(font.contains("\"ascent\":8"));
        assertTrue(font.contains(glyph.character()));

        String firstManifest = new String(Files.readAllBytes(build.resolve("glyph-allocations.yml")), StandardCharsets.UTF_8);
        compiler.compile(contentRoot.toPath(), build.resolve("second.zip"), renderAllocations, JavaPackTarget.MC_1_14_4);
        assertEquals(firstManifest, new String(Files.readAllBytes(build.resolve("glyph-allocations.yml")), StandardCharsets.UTF_8));
    }

    @Test
    public void defaultsScaleAndPositionAndRejectsInvalidPosition() throws Exception {
        File contentRoot = temporaryFolder.newFolder("explicit-content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());
        File texture = new File(pack, "assets/voxel/textures/ui/overlay.png");
        assertTrue(texture.getParentFile().mkdirs());
        writePng(texture, 40, 18);
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        File definition = new File(pack, "content/ui.yml");
        write(definition, "ui:\n  overlay:\n    path: ui/overlay.png\n");

        UiGlyphDefinition glyph = new UiGlyphLoader().load(contentRoot.toPath(),
                temporaryFolder.newFolder("explicit-build").toPath().resolve("glyphs.yml"), false)
                .get(ContentID.of("voxel", "overlay")).get();
        assertEquals(18, glyph.scaleRatio());
        assertEquals(8, glyph.yPosition());

        write(definition, "ui:\n  overlay:\n    path: ui/overlay.png\n    scale_ratio: 18\n    y_position: 19\n");
        try {
            new UiGlyphLoader().load(contentRoot.toPath(),
                    temporaryFolder.newFolder("invalid-build").toPath().resolve("bad-glyphs.yml"), false);
            fail("Expected invalid y_position to fail");
        } catch (RuntimeException exception) {
            assertTrue(exception.getMessage().contains("lower than or equal to scale_ratio"));
        }
    }

    @Test
    public void rejectsDuplicateExplicitSymbols() throws Exception {
        File contentRoot = temporaryFolder.newFolder("collision-content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());
        File first = new File(pack, "assets/voxel/textures/ui/first.png");
        File second = new File(pack, "assets/voxel/textures/ui/second.png");
        assertTrue(first.getParentFile().mkdirs());
        writePng(first, 16, 16);
        writePng(second, 16, 16);
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        write(new File(pack, "content/ui.yml"),
                "ui:\n  first:\n    path: ui/first.png\n    symbol: '\uE100'\n" +
                "  second:\n    path: ui/second.png\n    symbol: '\uE100'\n");
        try {
            new UiGlyphLoader().load(contentRoot.toPath(),
                    temporaryFolder.newFolder("collision-build").toPath().resolve("glyphs.yml"), false);
            fail("Expected duplicate symbol rejection");
        } catch (RuntimeException exception) {
            assertTrue(exception.getMessage().contains("Duplicate UI glyph codepoint"));
        }
    }

    @Test
    public void rejectsUiContentForPreFontTarget() throws Exception {
        File contentRoot = temporaryFolder.newFolder("legacy-content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());
        File texture = new File(pack, "assets/voxel/textures/ui/overlay.png");
        assertTrue(texture.getParentFile().mkdirs());
        writePng(texture, 16, 16);
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        write(new File(pack, "content/ui.yml"), "ui:\n  overlay:\n    path: ui/overlay.png\n");
        try {
            new JavaPackCompiler().compile(contentRoot.toPath(), temporaryFolder.newFile("legacy.zip").toPath(),
                    temporaryFolder.newFolder("legacy-build").toPath().resolve("render.yml"), JavaPackTarget.MC_1_12_2);
            fail("Expected pre-font target rejection");
        } catch (JavaPackCompileException exception) {
            assertTrue(exception.getMessage().contains("does not support custom UI fonts"));
        }
    }

    private static void write(File file, String content) throws Exception {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static void writePng(File file, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) image.setRGB(x, y, 0xFFFFFFFF);
        }
        assertTrue(ImageIO.write(image, "png", file));
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
            } finally { input.close(); }
        } finally { file.close(); }
    }
}
