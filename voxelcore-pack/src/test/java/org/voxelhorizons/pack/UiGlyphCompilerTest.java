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
                "  z_smile:\n    path: ui/smile.png\n    scale_ratio: 18\n");

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
        assertEquals(0xE000, glyph.codePoint());
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
        String permissionSample = ":z_smile::warps_menu::offset_-16:";
        assertEquals("\u00A7f" + inline.character() + ":warps_menu::offset_-16:",
                resolver.resolve(permissionSample, true, false));
        assertEquals(":z_smile:\u00A7f" + glyph.character()
                        + UiSpacingGlyphs.charactersForOffset(-177) + UiSpacingGlyphs.charactersForOffset(-16),
                resolver.resolve(permissionSample, false, true));
        assertEquals(permissionSample, resolver.resolve(permissionSample, false, false));

        String chest54 = resolver.resolve(":offset_-8::generic_54_top::offset_8:Chest");
        assertEquals(UiSpacingGlyphs.charactersForOffset(-8)
                        + "\u00A7f" + new String(Character.toChars(ContainerGuiGlyphs.GENERIC_54_TOP))
                        + UiSpacingGlyphs.charactersForOffset(-ContainerGuiGlyphs.ADVANCE)
                        + UiSpacingGlyphs.charactersForOffset(8) + "Chest",
                chest54);
        assertEquals(":generic_54_top:", resolver.resolve(":generic_54_top:", true, false));

        String font = zipText(output, "assets/minecraft/font/default.json");
        assertTrue(font.contains("\"type\":\"space\""));
        assertTrue(font.contains("\"type\":\"bitmap\""));
        assertTrue(font.contains("\"file\":\"voxel:ui/npc/warps_menu.png\""));
        assertTrue(font.contains("\"height\":18"));
        assertTrue(font.contains("\"ascent\":8"));
        assertTrue(font.contains(glyph.character()));
        assertTrue(font.contains("voxelcore:ui/tooltip/left.png"));
        assertTrue(font.contains(String.valueOf((char) TooltipGlyphs.BACKGROUND_LEFT)));
        assertTrue(zipText(output, "assets/voxelcore/font/tooltip_line1.json").contains("\"ascent\":4"));
        assertTrue(zipText(output, "assets/voxelcore/font/tooltip_line2.json").contains("\"ascent\":-1"));
        assertTrue(zipText(output, "assets/voxelcore/font/tooltip_line3.json").contains("\"ascent\":-6"));
        assertTrue(font.contains("voxelcore:container/gui/generic_27_top.png"));
        assertTrue(font.contains("voxelcore:container/gui/generic_54_top.png"));
        assertTrue(font.contains(String.valueOf((char) ContainerGuiGlyphs.GENERIC_27_TOP)));
        assertTrue(font.contains(String.valueOf((char) ContainerGuiGlyphs.GENERIC_54_TOP)));
        assertTrue(zipBytes(output, "assets/voxelcore/textures/container/gui/generic_27_top.png").length > 0);
        assertTrue(zipBytes(output, "assets/voxelcore/textures/container/gui/generic_54_top.png").length > 0);

        String firstManifest = new String(Files.readAllBytes(build.resolve("glyph-allocations.yml")), StandardCharsets.UTF_8);
        compiler.compile(contentRoot.toPath(), build.resolve("second.zip"), renderAllocations, JavaPackTarget.MC_1_14_4);
        assertEquals(firstManifest, new String(Files.readAllBytes(build.resolve("glyph-allocations.yml")), StandardCharsets.UTF_8));
    }

    @Test
    public void editableTooltipAssetsOverrideCompilerDefaults() throws Exception {
        File contentRoot = temporaryFolder.newFolder("tooltip-content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");

        File editable = temporaryFolder.newFolder("tooltip-assets");
        File left = new File(editable, "left.png");
        File center = new File(editable, "center.png");
        File right = new File(editable, "right.png");
        writePng(left, 2, 38);
        writePng(center, 2, 38);
        writePng(right, 2, 38);

        Path build = temporaryFolder.newFolder("tooltip-build").toPath();
        Path output = build.resolve("pack.zip");
        new JavaPackCompiler().compile(contentRoot.toPath(), output,
                build.resolve("render-allocations.yml"), JavaPackTarget.MC_1_14_4, editable.toPath());

        assertArrayEquals(Files.readAllBytes(left.toPath()),
                zipBytes(output, "assets/voxelcore/textures/ui/tooltip/left.png"));
        assertArrayEquals(Files.readAllBytes(center.toPath()),
                zipBytes(output, "assets/voxelcore/textures/ui/tooltip/center.png"));
        assertArrayEquals(Files.readAllBytes(right.toPath()),
                zipBytes(output, "assets/voxelcore/textures/ui/tooltip/right.png"));
    }

    @Test
    public void editableContainerGuiAssetsOverrideCompilerDefaults() throws Exception {
        File contentRoot = temporaryFolder.newFolder("container-content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");

        File tooltipAssets = temporaryFolder.newFolder("container-tooltip-assets");
        File editable = temporaryFolder.newFolder("container-gui-assets");
        File single = new File(editable, "generic_27_top.png");
        File doubleChest = new File(editable, "generic_54_top.png");
        writePng(single, 176, 85);
        writePng(doubleChest, 176, 139);

        Path build = temporaryFolder.newFolder("container-build").toPath();
        Path output = build.resolve("pack.zip");
        new JavaPackCompiler().compile(contentRoot.toPath(), output,
                build.resolve("render-allocations.yml"), JavaPackTarget.MC_1_14_4,
                tooltipAssets.toPath(), editable.toPath());

        assertPngPixelsEqual(single.toPath(),
                zipBytes(output, "assets/voxelcore/textures/container/gui/generic_27_top.png"));
        assertPngPixelsEqual(doubleChest.toPath(),
                zipBytes(output, "assets/voxelcore/textures/container/gui/generic_54_top.png"));
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

    private static void assertPngPixelsEqual(Path expected, byte[] actualBytes) throws Exception {
        BufferedImage expectedImage = ImageIO.read(expected.toFile());
        BufferedImage actualImage = ImageIO.read(new java.io.ByteArrayInputStream(actualBytes));
        assertEquals(expectedImage.getWidth(), actualImage.getWidth());
        assertEquals(expectedImage.getHeight(), actualImage.getHeight());
        for (int y = 0; y < expectedImage.getHeight(); y++) {
            for (int x = 0; x < expectedImage.getWidth(); x++) {
                assertEquals(expectedImage.getRGB(x, y), actualImage.getRGB(x, y));
            }
        }
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
