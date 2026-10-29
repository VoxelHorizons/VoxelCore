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

import static org.junit.Assert.assertArrayEquals;
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
        File inlineTexture = new File(pack, "assets/voxel/textures/ui/smile.png");
        writePng(inlineTexture, 18, 18);
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        write(new File(pack, "content/ui.yml"),
                "ui:\n  warps_menu:\n    path: ui/npc/warps_menu.png\n" +
                "    scale_ratio: 18\n    y_position: 8\n    gui: true\n" +
                "  z_smile:\n    path: ui/smile.png\n    scale_ratio: 18\n    bedrock_scale: 0.5\n");

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
        assertEquals(177, glyph.advance());
        assertTrue(glyph.gui());
        assertEquals(1.0D, glyph.bedrockScale(), 0.0D);
        assertEquals(0xE200, glyph.codePoint());
        assertEquals(glyph.character(), registry.resolveAliases(":warps_menu:"));
        assertEquals(glyph.character(), registry.resolveAliases(":voxel/warps_menu:"));

        UiTextResolver resolver = new UiTextResolver(registry);
        String rendered = resolver.resolve(":offset_-16::warps_menu:\u00A7rWarps");
        assertEquals(UiSpacingGlyphs.charactersForOffset(-16) + "\u00A7f" + glyph.character()
                + UiSpacingGlyphs.charactersForOffset(-177) + "\u00A7rWarps", rendered);
        assertEquals(UiSpacingGlyphs.charactersForOffset(-17), resolver.resolve(":offset_-17:"));
        assertEquals("", resolver.resolve(":offset_0:"));
        assertEquals(":offset_-2048:", resolver.resolve(":offset_-2048:"));

        UiGlyphDefinition inline = registry.get(ContentID.of("voxel", "z_smile")).get();
        assertTrue(!inline.gui());
        assertEquals(0.5D, inline.bedrockScale(), 0.0D);
        String permissionSample = ":z_smile::warps_menu::offset_-16:";
        assertEquals("\u00A7f" + inline.character() + ":warps_menu::offset_-16:",
                resolver.resolve(permissionSample, true, false));
        assertEquals(":z_smile:\u00A7f" + glyph.character()
                        + UiSpacingGlyphs.charactersForOffset(-177) + UiSpacingGlyphs.charactersForOffset(-16),
                resolver.resolve(permissionSample, false, true));
        assertEquals(permissionSample, resolver.resolve(permissionSample, false, false));

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
    public void retiresRemovedGlyphBeforeAllocatingNewSymbols() throws Exception {
        File contentRoot = temporaryFolder.newFolder("tombstone-content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());
        File texture = new File(pack, "assets/voxel/textures/ui/background.png");
        assertTrue(texture.getParentFile().mkdirs());
        writePng(texture, 16, 16);
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        File definition = new File(pack, "content/ui.yml");
        write(definition, "ui:\n  skills_background:\n    path: ui/background.png\n");
        Path allocations = temporaryFolder.newFolder("tombstone-build").toPath().resolve("glyph-allocations.yml");

        UiGlyphLoader loader = new UiGlyphLoader();
        UiGlyphDefinition removed = loader.load(contentRoot.toPath(), allocations, true)
                .get(ContentID.of("voxel", "skills_background")).get();
        assertEquals(0xE200, removed.codePoint());

        write(definition, "ui:\n  replacement_background:\n    path: ui/background.png\n");
        UiGlyphDefinition replacement = loader.load(contentRoot.toPath(), allocations, true)
                .get(ContentID.of("voxel", "replacement_background")).get();
        assertEquals(0xE201, replacement.codePoint());

        UiGlyphDefinition stableReplacement = loader.load(contentRoot.toPath(), allocations, false)
                .get(ContentID.of("voxel", "replacement_background")).get();
        assertEquals(0xE201, stableReplacement.codePoint());
    }

    @Test
    public void repairsPersistedCollisionWithInactiveGlyph() throws Exception {
        File contentRoot = temporaryFolder.newFolder("stale-tombstone-content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());
        File texture = new File(pack, "assets/voxel/textures/ui/background.png");
        assertTrue(texture.getParentFile().mkdirs());
        writePng(texture, 16, 16);
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        write(new File(pack, "content/ui.yml"), "ui:\n  replacement_background:\n    path: ui/background.png\n");
        Path allocations = temporaryFolder.newFolder("stale-tombstone-build").toPath().resolve("glyph-allocations.yml");
        write(allocations.toFile(), "schema: 1\nglyphs:\n  'voxel:replacement_background': 57344\n"
                + "inactive:\n  'voxel:skills_background': 57344\n");

        UiGlyphDefinition repaired = new UiGlyphLoader().load(contentRoot.toPath(), allocations, true)
                .get(ContentID.of("voxel", "replacement_background")).get();
        assertEquals(0xE200, repaired.codePoint());

        String manifest = new String(Files.readAllBytes(allocations), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("'voxel:replacement_background': 57856"));
        assertTrue(manifest.contains("'voxel:skills_background': 57857"));
    }

    @Test
    public void migratesActiveAndInactiveBedrockReservedGlyphsWithBackupAndReport() throws Exception {
        File contentRoot = temporaryFolder.newFolder("bedrock-migrate-content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());
        File texture = new File(pack, "assets/voxel/textures/ui/icon.png");
        assertTrue(texture.getParentFile().mkdirs());
        writePng(texture, 16, 16);
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        write(new File(pack, "content/ui.yml"),
                "ui:\n  rank:\n    path: ui/icon.png\n    symbol: '" + Character.toString((char) 0xE025) + "'\n");
        Path allocations = temporaryFolder.newFolder("migration-output").toPath().resolve("glyph-allocations.yml");
        String original = "schema: 1\nglyphs:\n  'voxel:rank': 57381\ninactive:\n  'voxel:retired': 57344\n";
        write(allocations.toFile(), original);
        UiGlyphLoader loader = new UiGlyphLoader();
        UiGlyphDefinition rank = loader.load(contentRoot.toPath(), allocations, true)
                .get(ContentID.of("voxel", "rank")).get();
        assertEquals(0xE200, rank.codePoint());
        String migrated = new String(Files.readAllBytes(allocations), StandardCharsets.UTF_8);
        assertTrue(migrated.contains("'voxel:rank': 57856"));
        assertTrue(migrated.contains("'voxel:retired': 57857"));
        assertEquals(original, new String(Files.readAllBytes(allocations.resolveSibling(
                "glyph-allocations.yml.before-bedrock-migration")), StandardCharsets.UTF_8));
        String report = new String(Files.readAllBytes(allocations.resolveSibling(
                "glyph-bedrock-migration.txt")), StandardCharsets.UTF_8);
        assertTrue(report.contains("U+E025 -> U+E200"));
        assertTrue(report.contains("U+E000 -> U+E201"));
        loader.load(contentRoot.toPath(), allocations, true);
        assertEquals(migrated, new String(Files.readAllBytes(allocations), StandardCharsets.UTF_8));
    }

    @Test
    public void noLongerEmbedsTooltipBackgroundTextures() throws Exception {
        File contentRoot = temporaryFolder.newFolder("tooltip-content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        Path build = temporaryFolder.newFolder("tooltip-build").toPath();
        Path output = build.resolve("pack.zip");
        new JavaPackCompiler().compile(contentRoot.toPath(), output,
                build.resolve("render-allocations.yml"), JavaPackTarget.MC_1_14_4);
        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertTrue(zip.getEntry("assets/voxelcore/textures/ui/tooltip/left.png") == null);
            assertTrue(zip.getEntry("assets/voxelcore/textures/ui/tooltip/center.png") == null);
            assertTrue(zip.getEntry("assets/voxelcore/textures/ui/tooltip/right.png") == null);
        }
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
        assertEquals(41, glyph.advance());
        assertTrue(!glyph.gui());

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
                "ui:\n  first:\n    path: ui/first.png\n    symbol: '\uE300'\n" +
                "  second:\n    path: ui/second.png\n    symbol: '\uE300'\n");
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

    private static byte[] zipBytes(Path zip, String name) throws Exception {
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
                return output.toByteArray();
            } finally { input.close(); }
        } finally { file.close(); }
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
