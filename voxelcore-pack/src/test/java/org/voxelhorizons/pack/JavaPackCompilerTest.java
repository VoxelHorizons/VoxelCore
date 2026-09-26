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
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void keepsDurabilityAndNumericCustomModelDataIndependentAcrossTargets() throws Exception {
        File contentRoot = temporaryFolder.newFolder("content");
        File pack = new File(contentRoot, "mypack");
        File content = new File(pack, "content/items");
        File models = new File(pack, "assets/mypack/models/item");
        File textures = new File(pack, "assets/mypack/textures/item");
        assertTrue(content.mkdirs()); assertTrue(models.mkdirs()); assertTrue(textures.mkdirs());

        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: mypack\n");
        write(new File(content, "gems.yml"),
                "items:\n" +
                "  gem_base:\n" +
                "    material: minecraft:diamond_hoe\n" +
                "  ruby:\n" +
                "    extends: gem_base\n" +
                "    display_name: Ruby\n" +
                "    render:\n" +
                "      model: mypack:item/ruby\n" +
                "      durability: 7\n" +
                "      custom_model_data: 1001\n" +
                "  sapphire:\n" +
                "    extends: gem_base\n" +
                "    display_name: Sapphire\n" +
                "    render:\n" +
                "      model: mypack:item/sapphire\n" +
                "      durability: 11\n" +
                "      custom_model_data: 1002\n");
        write(new File(models, "ruby.json"), "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mypack:item/ruby\"}}\n");
        write(new File(models, "sapphire.json"), "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mypack:item/sapphire\"}}\n");
        write(new File(textures, "ruby.png"), "ruby-bytes");
        write(new File(textures, "sapphire.png"), "sapphire-bytes");

        Path build = temporaryFolder.newFolder("build").toPath();
        Path allocations = build.resolve("render-allocations.yml");
        JavaPackCompiler compiler = new JavaPackCompiler();

        Path damage = build.resolve("damage.zip");
        JavaPackBuildResult damageResult = compiler.compile(contentRoot.toPath(), damage, allocations,
                JavaPackTarget.legacyDamage("1.12-test", 3));
        assertEquals(2, damageResult.renderedItems());
        String hoeDamage = zipText(damage, "assets/minecraft/models/item/diamond_hoe.json");
        assertTrue(hoeDamage.contains("\"damaged\":0"));
        assertTrue(hoeDamage.contains("\"model\":\"mypack:item/ruby\""));
        assertTrue(hoeDamage.contains("\"damage\":0.0044843"));
        assertTrue(hoeDamage.contains("\"damage\":0.00704676"));
        assertTrue(!hoeDamage.contains("1001"));
        assertTrue(!hoeDamage.contains("1002"));

        String allocationText = new String(Files.readAllBytes(allocations), StandardCharsets.UTF_8);
        assertTrue(allocationText.contains("schema: 3"));
        assertTrue(allocationText.contains("custom_model_data: 1001"));
        assertTrue(allocationText.contains("custom_model_data: 1002"));
        assertTrue(allocationText.contains("structured_model_data:"));
        assertTrue(!allocationText.contains("legacy_custom_model_data"));

        Path numeric = build.resolve("numeric.zip");
        compiler.compile(contentRoot.toPath(), numeric, allocations, JavaPackTarget.numericCmd("1.14-test", 4));
        String hoeNumeric = zipText(numeric, "assets/minecraft/models/item/diamond_hoe.json");
        assertTrue(hoeNumeric.contains("\"custom_model_data\":1001"));
        assertTrue(hoeNumeric.contains("\"custom_model_data\":1002"));
        assertTrue(!hoeNumeric.contains("\"damage\":0.0044843"));

        Path modern = build.resolve("modern.zip");
        compiler.compile(contentRoot.toPath(), modern, allocations, JavaPackTarget.modern("1.21.4-test", 46));
        assertTrue(hasEntry(modern, "assets/mypack/items/item/ruby.json"));
        assertEquals(allocationText, new String(Files.readAllBytes(allocations), StandardCharsets.UTF_8));

        Path secondNumeric = build.resolve("numeric-second.zip");
        compiler.compile(contentRoot.toPath(), secondNumeric, allocations, JavaPackTarget.numericCmd("1.14-test", 4));
        assertEquals(java.util.Arrays.toString(Files.readAllBytes(numeric)), java.util.Arrays.toString(Files.readAllBytes(secondNumeric)));
    }

    @Test
    public void generatesOversizedInGuiForSupportedModernTargets() throws Exception {
        File contentRoot = temporaryFolder.newFolder("oversized-content");
        File pack = new File(contentRoot, "mypack");
        assertTrue(new File(pack, "content").mkdirs());
        assertTrue(new File(pack, "assets/mypack/models/item").mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: mypack\n");
        write(new File(pack, "content/ui.yml"),
                "items:\n" +
                "  toggle_off:\n" +
                "    material: minecraft:paper\n" +
                "    render:\n" +
                "      model: mypack:item/toggle_off\n" +
                "      oversized_in_gui: true\n");
        write(new File(pack, "assets/mypack/models/item/toggle_off.json"),
                "{\"parent\":\"minecraft:item/generated\"}\n");

        Path build = temporaryFolder.newFolder("oversized-build").toPath();
        Path allocations = build.resolve("allocations.yml");
        JavaPackCompiler compiler = new JavaPackCompiler();

        Path newest = build.resolve("mc-26.2.zip");
        compiler.compile(contentRoot.toPath(), newest, allocations, JavaPackTarget.MC_26_2);
        String newestDefinition = zipText(newest, "assets/mypack/items/item/toggle_off.json");
        assertTrue(newestDefinition.contains("\"oversized_in_gui\":true"));
        assertTrue(newestDefinition.contains("\"type\":\"minecraft:model\""));

        Path oldModern = build.resolve("mc-1.21.4.zip");
        compiler.compile(contentRoot.toPath(), oldModern, allocations, JavaPackTarget.MC_1_21_4);
        String oldDefinition = zipText(oldModern, "assets/mypack/items/item/toggle_off.json");
        assertTrue(!oldDefinition.contains("oversized_in_gui"));
    }

    @Test
    public void writesModernRangeMetadataForMinecraft262() throws Exception {
        File contentRoot = temporaryFolder.newFolder("metadata-content");
        File pack = new File(contentRoot, "mypack");
        assertTrue(new File(pack, "content").mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: mypack\n");
        Path build = temporaryFolder.newFolder("metadata-build").toPath();
        Path zip = build.resolve("mc-26.2.zip");
        new JavaPackCompiler().compile(contentRoot.toPath(), zip, build.resolve("allocations.yml"), JavaPackTarget.MC_26_2);
        String metadata = zipText(zip, "pack.mcmeta");
        assertTrue(metadata.contains("\"min_format\":[88,0]"));
        assertTrue(metadata.contains("\"max_format\":[88,0]"));
        assertTrue(!metadata.contains("\"pack_format\""));
    }

    @Test
    public void compilesStructuredModernDecisionTreeUsingStableIndices() throws Exception {
        File contentRoot = temporaryFolder.newFolder("rule-content");
        File pack = new File(contentRoot, "mypack");
        assertTrue(new File(pack, "content").mkdirs());
        assertTrue(new File(pack, "assets/mypack/models/item").mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: mypack\n");
        write(new File(pack, "content/ruby.yml"),
                "items:\n  ruby:\n    material: minecraft:paper\n    render:\n      model: mypack:item/ruby\n" +
                "      custom_model_data:\n        variant: red\n        powered: true\n        intensity: 0.75\n        tint: '#ff0000'\n" +
                "      rule:\n        select:\n          key: variant\n          cases:\n            red:\n              condition:\n                key: powered\n                true:\n                  range:\n                    key: intensity\n                    entries:\n                      '0.75':\n                        model:\n                          id: mypack:item/powered\n                          tint: tint\n                    fallback: mypack:item/ruby\n                false: mypack:item/ruby\n          fallback: mypack:item/ruby\n");
        write(new File(pack, "assets/mypack/models/item/ruby.json"), "{\"parent\":\"minecraft:item/generated\"}\n");
        write(new File(pack, "assets/mypack/models/item/powered.json"), "{\"parent\":\"minecraft:item/generated\"}\n");

        Path build = temporaryFolder.newFolder("rule-build").toPath();
        Path allocations = build.resolve("render-allocations.yml");
        Path zip = build.resolve("modern.zip");
        new JavaPackCompiler().compile(contentRoot.toPath(), zip, allocations, JavaPackTarget.MC_1_21_4);

        String itemInfo = zipText(zip, "assets/mypack/items/item/ruby.json");
        assertTrue(itemInfo.contains("\"type\":\"minecraft:select\""));
        assertTrue(itemInfo.contains("\"type\":\"minecraft:condition\""));
        assertTrue(itemInfo.contains("\"type\":\"minecraft:range_dispatch\""));
        assertTrue(itemInfo.contains("\"tints\":[{\"type\":\"minecraft:custom_model_data\""));
        assertTrue(itemInfo.contains("\"index\":0"));

        String manifest = new String(Files.readAllBytes(allocations), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("'intensity': 0"));
        assertTrue(manifest.contains("'powered': 0"));
        assertTrue(manifest.contains("'variant': 0"));
        assertTrue(manifest.contains("'tint': 0"));
    }

    @Test
    public void acceptsStructuredCustomModelDataOnlyForModernTargets() throws Exception {
        File contentRoot = temporaryFolder.newFolder("modern-content");
        File pack = new File(contentRoot, "mypack");
        assertTrue(new File(pack, "content").mkdirs());
        assertTrue(new File(pack, "assets/mypack/models/item").mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: mypack\n");
        write(new File(pack, "content/ruby.yml"),
                "items:\n  ruby:\n    material: minecraft:paper\n    render:\n      model: mypack:item/ruby\n" +
                "      custom_model_data:\n        variant: ruby\n        powered: true\n        intensity: 0.75\n        tint: '#ff0000'\n");
        write(new File(pack, "assets/mypack/models/item/ruby.json"), "{\"parent\":\"minecraft:item/generated\"}\n");
        Path allocations = temporaryFolder.newFolder("modern-build").toPath().resolve("allocations.yml");
        new JavaPackCompiler().compile(contentRoot.toPath(), temporaryFolder.newFile("modern.zip").toPath(), allocations,
                JavaPackTarget.modern("modern", 46));
        try {
            new JavaPackCompiler().compile(contentRoot.toPath(), temporaryFolder.newFile("numeric.zip").toPath(), allocations,
                    JavaPackTarget.numericCmd("numeric", 22));
            fail("Expected structured custom_model_data to be rejected for numeric CMD target");
        } catch (JavaPackCompileException exception) {
            assertTrue(exception.getMessage().contains("Structured custom_model_data requires a 1.21.4+"));
        }
    }

    @Test
    public void ignoresStructuredCustomModelDataForLegacyDamageTarget() throws Exception {
        File contentRoot = temporaryFolder.newFolder("legacy-content");
        File pack = new File(contentRoot, "mypack");
        assertTrue(new File(pack, "content").mkdirs());
        assertTrue(new File(pack, "assets/mypack/models/item").mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: mypack\n");
        write(new File(pack, "content/ruby.yml"),
                "items:\n  ruby:\n    material: minecraft:diamond_hoe\n    render:\n      model: mypack:item/ruby\n" +
                "      durability: 7\n      unbreakable: true\n" +
                "      custom_model_data:\n        variant: ignored\n        powered: true\n");
        write(new File(pack, "assets/mypack/models/item/ruby.json"), "{\"parent\":\"minecraft:item/generated\"}\n");
        Path allocations = temporaryFolder.newFolder("legacy-build").toPath().resolve("allocations.yml");
        Path legacy = temporaryFolder.newFile("legacy.zip").toPath();
        new JavaPackCompiler().compile(contentRoot.toPath(), legacy, allocations,
                JavaPackTarget.legacyDamage("legacy", 3));
        String json = zipText(legacy, "assets/minecraft/models/item/diamond_hoe.json");
        assertTrue(json.contains("\"damage\":0.0044843"));
        assertTrue(!json.contains("custom_model_data"));
    }

    @Test
    public void requiresDeclaredDependencyForCrossPackInheritance() throws Exception {
        File contentRoot = temporaryFolder.newFolder("dependency-content");
        File core = new File(contentRoot, "core");
        File addon = new File(contentRoot, "addon");
        assertTrue(new File(core, "content").mkdirs()); assertTrue(new File(addon, "content").mkdirs());
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

    @Test
    public void generatesCustomTextureAtlasOnlyForApplicableTargets() throws Exception {
        File contentRoot = temporaryFolder.newFolder("atlas-content");
        File pack = new File(contentRoot, "mypack");
        assertTrue(new File(pack, "content").mkdirs());
        assertTrue(new File(pack, "assets/mypack/textures/ui").mkdirs());
        assertTrue(new File(pack, "assets/mypack/textures/item").mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: mypack\n");
        write(new File(pack, "assets/mypack/textures/ui/blank.png"), "ui-bytes");
        write(new File(pack, "assets/mypack/textures/item/normal.png"), "item-bytes");

        Path build = temporaryFolder.newFolder("atlas-build").toPath();
        Path allocations = build.resolve("allocations.yml");
        JavaPackCompiler compiler = new JavaPackCompiler();

        Path combined = build.resolve("mc-1.21.4.zip");
        compiler.compile(contentRoot.toPath(), combined, allocations, JavaPackTarget.MC_1_21_4);
        String blocksAtlas = zipText(combined, "assets/minecraft/atlases/blocks.json");
        assertTrue(blocksAtlas.contains("\"source\":\"ui\""));
        assertTrue(blocksAtlas.contains("\"prefix\":\"ui/\""));
        assertTrue(!blocksAtlas.contains("\"source\":\"item\""));
        assertTrue(!hasEntry(combined, "assets/minecraft/atlases/items.json"));

        Path split = build.resolve("mc-26.2.zip");
        compiler.compile(contentRoot.toPath(), split, allocations, JavaPackTarget.MC_26_2);
        String itemsAtlas = zipText(split, "assets/minecraft/atlases/items.json");
        assertTrue(itemsAtlas.contains("\"source\":\"ui\""));
        assertTrue(!hasEntry(split, "assets/minecraft/atlases/blocks.json"));

        Path legacy = build.resolve("mc-1.14.4.zip");
        compiler.compile(contentRoot.toPath(), legacy, allocations, JavaPackTarget.MC_1_14_4);
        assertTrue(!hasEntry(legacy, "assets/minecraft/atlases/blocks.json"));
        assertTrue(!hasEntry(legacy, "assets/minecraft/atlases/items.json"));
    }

    private static void write(File file, String content) throws Exception {
        File parent = file.getParentFile(); if (parent != null && !parent.exists()) assertTrue(parent.mkdirs());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
    private static boolean hasEntry(Path zip, String name) throws Exception {
        ZipFile file = new ZipFile(zip.toFile()); try { return file.getEntry(name) != null; } finally { file.close(); }
    }
    private static String zipText(Path zip, String name) throws Exception {
        ZipFile file = new ZipFile(zip.toFile());
        try {
            ZipEntry entry = file.getEntry(name); if (entry == null) throw new AssertionError("Missing zip entry " + name);
            InputStream input = file.getInputStream(entry);
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[1024]; int read;
                while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            } finally { input.close(); }
        } finally { file.close(); }
    }
}
