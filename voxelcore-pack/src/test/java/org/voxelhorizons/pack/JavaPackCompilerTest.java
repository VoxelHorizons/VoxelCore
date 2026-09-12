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
import static org.junit.Assert.fail;

public class JavaPackCompilerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void compilesStableLegacyAndModernPacksFromNamespacedAssets() throws Exception {
        File contentRoot = temporaryFolder.newFolder("content");
        File pack = new File(contentRoot, "mypack");
        File content = new File(pack, "content/items");
        File models = new File(pack, "assets/mypack/models/item");
        File textures = new File(pack, "assets/mypack/textures/item");
        assertTrue(content.mkdirs());
        assertTrue(models.mkdirs());
        assertTrue(textures.mkdirs());

        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: mypack\n");
        write(new File(content, "gems.yml"),
                "items:\n" +
                "  gem_base:\n" +
                "    material: minecraft:paper\n" +
                "  ruby:\n" +
                "    extends: gem_base\n" +
                "    display_name: Ruby\n" +
                "    render:\n" +
                "      model: mypack:item/ruby\n" +
                "  sapphire:\n" +
                "    extends: gem_base\n" +
                "    display_name: Sapphire\n" +
                "    render:\n" +
                "      model: mypack:item/sapphire\n");
        write(new File(models, "ruby.json"), "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mypack:item/ruby\"}}\n");
        write(new File(models, "sapphire.json"), "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mypack:item/sapphire\"}}\n");
        write(new File(textures, "ruby.png"), "ruby-bytes");
        write(new File(textures, "sapphire.png"), "sapphire-bytes");

        Path build = temporaryFolder.newFolder("build").toPath();
        Path allocations = build.resolve("render-allocations.yml");
        Path legacy = build.resolve("legacy.zip");
        Path modern = build.resolve("modern.zip");
        JavaPackCompiler compiler = new JavaPackCompiler();

        JavaPackBuildResult legacyResult = compiler.compile(contentRoot.toPath(), legacy, allocations,
                JavaPackTarget.numericCmd("numeric-cmd-test", 22));
        assertEquals(2, legacyResult.renderedItems());
        assertEquals(4, legacyResult.copiedAssets());
        String allocationText = new String(Files.readAllBytes(allocations), StandardCharsets.UTF_8);
        assertTrue(allocationText.contains("'mypack:ruby'"));
        assertTrue(allocationText.contains("'mypack:sapphire'"));
        assertTrue(allocationText.contains("legacy_custom_model_data: 1000"));
        assertTrue(allocationText.contains("legacy_custom_model_data: 1001"));

        String paper = zipText(legacy, "assets/minecraft/models/item/paper.json");
        assertTrue(paper.contains("\"custom_model_data\": 1000"));
        assertTrue(paper.contains("\"model\": \"mypack:item/ruby\""));
        assertTrue(paper.contains("\"custom_model_data\": 1001"));
        assertTrue(paper.contains("\"model\": \"mypack:item/sapphire\""));
        assertTrue(hasEntry(legacy, "assets/mypack/models/item/ruby.json"));
        assertTrue(hasEntry(legacy, "assets/mypack/textures/item/ruby.png"));

        compiler.compile(contentRoot.toPath(), modern, allocations, JavaPackTarget.modern("1.21.4-test", 46));
        assertTrue(hasEntry(modern, "assets/mypack/items/item/ruby.json"));
        String rubyItem = zipText(modern, "assets/mypack/items/item/ruby.json");
        assertTrue(rubyItem.contains("\"type\": \"minecraft:model\""));
        assertTrue(rubyItem.contains("\"model\": \"mypack:item/ruby\""));

        String afterModern = new String(Files.readAllBytes(allocations), StandardCharsets.UTF_8);
        assertEquals(allocationText, afterModern);

        Path secondLegacy = build.resolve("legacy-second.zip");
        compiler.compile(contentRoot.toPath(), secondLegacy, allocations, JavaPackTarget.numericCmd("numeric-cmd-test", 22));
        assertEquals(java.util.Arrays.toString(Files.readAllBytes(legacy)),
                java.util.Arrays.toString(Files.readAllBytes(secondLegacy)));
    }

    @Test
    public void requiresDeclaredDependencyForCrossPackInheritance() throws Exception {
        File contentRoot = temporaryFolder.newFolder("dependency-content");
        File core = new File(contentRoot, "core");
        File addon = new File(contentRoot, "addon");
        assertTrue(new File(core, "content").mkdirs());
        assertTrue(new File(addon, "content").mkdirs());
        write(new File(core, "pack.yml"), "schema: 1\nnamespace: core\n");
        write(new File(core, "content/base.yml"), "items:\n  base:\n    material: minecraft:paper\n");
        write(new File(addon, "pack.yml"), "schema: 1\nnamespace: addon\n");
        write(new File(addon, "content/item.yml"), "items:\n  child:\n    extends: core:base\n");

        try {
            new JavaPackCompiler().compile(contentRoot.toPath(), temporaryFolder.newFile("bad.zip").toPath(),
                    temporaryFolder.newFile("allocations.yml").toPath(), JavaPackTarget.numericCmd("test", 22));
            fail("Expected undeclared dependency failure");
        } catch (RuntimeException exception) {
            assertTrue(exception.getMessage().contains("does not declare dependency 'core'"));
        }
    }

    private static void write(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) assertTrue(parent.mkdirs());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean hasEntry(Path zip, String name) throws Exception {
        ZipFile file = new ZipFile(zip.toFile());
        try { return file.getEntry(name) != null; }
        finally { file.close(); }
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
