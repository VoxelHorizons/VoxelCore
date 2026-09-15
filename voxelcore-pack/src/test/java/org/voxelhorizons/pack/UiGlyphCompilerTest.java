package org.voxelhorizons.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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
    public void compilesOneRowUiWithStableAutomaticGlyphAndSpacing() throws Exception {
        File contentRoot = temporaryFolder.newFolder("content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());
        File texture = new File(pack, "assets/voxel/textures/ui/npc/warps_menu.png");
        assertTrue(texture.getParentFile().mkdirs());
        writePng(texture, 256, 256);
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        write(new File(pack, "content/ui.yml"),
                "ui:\n" +
                "  npc_warps:\n" +
                "    texture: voxel:ui/npc/warps_menu\n" +
                "    inventory:\n" +
                "      rows: 1\n" +
                "    font:\n" +
                "      height: 256\n" +
                "      ascent: auto\n");

        Path build = temporaryFolder.newFolder("build").toPath();
        Path renderAllocations = build.resolve("render-allocations.yml");
        Path output = build.resolve("pack.zip");
        JavaPackCompiler compiler = new JavaPackCompiler();
        compiler.compile(contentRoot.toPath(), output, renderAllocations, JavaPackTarget.MC_1_14_4);

        UiGlyphRegistry registry = compiler.loadUiGlyphs(contentRoot.toPath(),
                build.resolve("glyph-allocations.yml"), false);
        UiGlyphDefinition glyph = registry.get(org.voxelhorizons.content.ContentID.of("voxel", "npc_warps")).get();
        assertEquals(1, glyph.rows());
        assertEquals(256, glyph.height());
        assertEquals(256, glyph.ascent());
        assertEquals(0xE000, glyph.codePoint());

        String font = zipText(output, "assets/minecraft/font/default.json");
        assertTrue(font.contains("\"type\":\"space\""));
        assertTrue(font.contains("\"type\":\"bitmap\""));
        assertTrue(font.contains("\"file\":\"voxel:ui/npc/warps_menu.png\""));
        assertTrue(font.contains("\"ascent\":256"));
        assertTrue(font.contains(glyph.character()));

        String firstManifest = new String(Files.readAllBytes(build.resolve("glyph-allocations.yml")), StandardCharsets.UTF_8);
        compiler.compile(contentRoot.toPath(), build.resolve("second.zip"), renderAllocations, JavaPackTarget.MC_1_14_4);
        assertEquals(firstManifest, new String(Files.readAllBytes(build.resolve("glyph-allocations.yml")), StandardCharsets.UTF_8));
    }

    @Test
    public void supportsExplicitAscentAndRejectsInvalidRows() throws Exception {
        File contentRoot = temporaryFolder.newFolder("explicit-content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());
        File texture = new File(pack, "assets/voxel/textures/ui/overlay.png");
        assertTrue(texture.getParentFile().mkdirs());
        writePng(texture, 256, 256);
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        File definition = new File(pack, "content/ui.yml");
        write(definition,
                "ui:\n  overlay:\n    texture: voxel:ui/overlay\n    inventory:\n      rows: 6\n" +
                "    font:\n      height: 40\n      ascent: 31\n");

        UiGlyphDefinition glyph = new UiGlyphLoader().load(contentRoot.toPath(),
                temporaryFolder.newFolder("explicit-build").toPath().resolve("glyphs.yml"), false)
                .get(org.voxelhorizons.content.ContentID.of("voxel", "overlay")).get();
        assertEquals(6, glyph.rows());
        assertEquals(31, glyph.ascent());

        write(definition,
                "ui:\n  overlay:\n    texture: voxel:ui/overlay\n    inventory:\n      rows: 7\n" +
                "    font:\n      height: 40\n");
        try {
            new UiGlyphLoader().load(contentRoot.toPath(), temporaryFolder.newFolder("invalid-build").toPath().resolve("bad-glyphs.yml"), false);
            fail("Expected invalid row count to fail");
        } catch (RuntimeException exception) {
            assertTrue(exception.getMessage().contains("between 1 and 6"));
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
        write(new File(pack, "content/ui.yml"),
                "ui:\n  overlay:\n    texture: voxel:ui/overlay\n    inventory:\n      rows: 1\n" +
                "    font:\n      height: 16\n");
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
        assertTrue(ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", file));
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
