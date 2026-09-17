package org.voxelhorizons.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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

public class CustomBlockPackCompilerTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void generatesModernBlockModelAndCarrierState() throws Exception {
        File root = content("modern");
        Path build = temporaryFolder.newFolder("modern-build").toPath();
        Path zip = build.resolve("pack.zip");
        JavaPackBuildResult result = new JavaPackCompiler().compile(root.toPath(), zip,
                build.resolve("render-allocations.yml"), JavaPackTarget.MC_1_21_4);
        assertEquals(1, result.renderedBlocks());
        assertEquals(1, result.renderedItems());
        assertTrue(zipText(zip, "assets/test/models/block/ruby_ore.json").contains("test:block/ruby_ore"));
        assertTrue(zipText(zip, "assets/test/items/block/ruby_ore.json").contains("test:block/ruby_ore"));
        assertTrue(zipText(zip, "assets/minecraft/blockstates/note_block.json").contains("test:block/ruby_ore"));
        assertTrue(Files.readAllBytes(build.resolve("block-allocations.yml")).length > 0);

        Path numericBuild = temporaryFolder.newFolder("numeric-build").toPath();
        Path numericZip = numericBuild.resolve("pack.zip");
        new JavaPackCompiler().compile(root.toPath(), numericZip,
                numericBuild.resolve("render-allocations.yml"), JavaPackTarget.MC_1_14_4);
        assertTrue(zipText(numericZip, "assets/minecraft/models/item/paper.json")
                .contains("test:block/ruby_ore"));
    }

    @Test
    public void generatesLegacyMushroomCarrierState() throws Exception {
        File root = content("legacy");
        Path build = temporaryFolder.newFolder("legacy-build").toPath();
        Path zip = build.resolve("pack.zip");
        new JavaPackCompiler().compile(root.toPath(), zip, build.resolve("render-allocations.yml"),
                JavaPackTarget.MC_1_12_2);
        assertTrue(zipText(zip, "assets/minecraft/blockstates/brown_mushroom_block.json")
                .contains("test:block/ruby_ore"));
        assertTrue(zipText(zip, "assets/minecraft/models/item/diamond_hoe.json")
                .contains("test:block/ruby_ore"));
    }

    private File content(String name) throws Exception {
        File root = temporaryFolder.newFolder(name + "-content");
        File pack = new File(root, "test");
        assertTrue(new File(pack, "content").mkdirs());
        assertTrue(new File(pack, "assets/test/textures/block").mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: test\n");
        write(new File(pack, "content/blocks.yml"),
                "blocks:\n  ruby_ore:\n    display_name: '&cRuby Ore'\n    method: auto\n" +
                "    model: cube_all\n    texture: test:block/ruby_ore\n" +
                "    drop: test:ruby_ore\n    silk_touch: test:ruby_ore\n");
        write(new File(pack, "assets/test/textures/block/ruby_ore.png"), "texture-bytes");
        return root;
    }

    private static void write(File file, String value) throws Exception {
        Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
    }

    private static String zipText(Path zip, String name) throws Exception {
        ZipFile file = new ZipFile(zip.toFile());
        try {
            ZipEntry entry = file.getEntry(name);
            if (entry == null) throw new AssertionError("Missing ZIP entry " + name);
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
