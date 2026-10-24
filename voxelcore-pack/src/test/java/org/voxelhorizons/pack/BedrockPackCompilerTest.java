package org.voxelhorizons.pack;

import com.google.gson.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

public class BedrockPackCompilerTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Path fixture() throws Exception {
        Path root = temp.newFolder().toPath();
        write(root.resolve("demo/pack.yml"), "schema: 1\nnamespace: demo\n");
        write(root.resolve("demo/content/items.yml"), "items:\n  token:\n    material: minecraft:paper\n    render:\n      model: demo:item/token\n    properties:\n      bedrock:\n        texture: demo:item/token\n  unsupported:\n    material: minecraft:paper\n    render:\n      model: demo:item/other\n");
        Path texture = root.resolve("demo/assets/demo/textures/item/token.png");
        Files.createDirectories(texture.getParent());
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(16,16,2), "png", texture.toFile());
        return root;
    }
    @Test public void compilesMatchingPackAndMappingsDeterministically() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        BedrockPackCompiler compiler = new BedrockPackCompiler();
        BedrockPackCompiler.Result first = compiler.compile(root,out), second = compiler.compile(root,out);
        assertEquals(first.revision, second.revision); assertEquals(1, first.mappedItems); assertEquals(1, first.unsupportedItems);
        JsonObject mappings = JsonParser.parseString(new String(Files.readAllBytes(first.directory.resolve("voxelcore-items.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject item = mappings.getAsJsonObject("items").getAsJsonArray("minecraft:paper").get(0).getAsJsonObject();
        assertEquals("demo:item/token", item.get("model").getAsString());
        try (ZipFile zip = new ZipFile(first.directory.resolve("voxelcore.mcpack").toFile())) {
            assertNotNull(zip.getEntry("manifest.json")); assertNotNull(zip.getEntry("textures/item_texture.json"));
            assertEquals(3, zip.size());
        }
        Path geyser = temp.newFolder().toPath();
        write(geyser.resolve("packs/other.mcpack"), "unrelated");
        new GeyserPackPublisher().publish(first, geyser);
        assertArrayEquals(Files.readAllBytes(first.directory.resolve("voxelcore.mcpack")),Files.readAllBytes(geyser.resolve("packs/voxelcore.mcpack")));
        assertTrue(Files.exists(geyser.resolve("packs/other.mcpack")));
    }
    @Test public void missingTextureDoesNotReplacePreviousRevision() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        BedrockPackCompiler compiler = new BedrockPackCompiler();
        BedrockPackCompiler.Result first = compiler.compile(root,out);
        byte[] original = Files.readAllBytes(first.directory.resolve("voxelcore.mcpack"));
        Files.delete(root.resolve("demo/assets/demo/textures/item/token.png"));
        try { compiler.compile(root,out); fail("Missing texture must fail"); }
        catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("Missing Bedrock item texture")); }
        assertArrayEquals(original, Files.readAllBytes(first.directory.resolve("voxelcore.mcpack")));
    }
    @Test public void textureChangeCreatesNewRevisionAndKeepsPrevious() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        BedrockPackCompiler compiler = new BedrockPackCompiler();
        BedrockPackCompiler.Result first = compiler.compile(root,out);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(16,16,2); image.setRGB(0,0,0xffff0000);
        javax.imageio.ImageIO.write(image,"png",root.resolve("demo/assets/demo/textures/item/token.png").toFile());
        BedrockPackCompiler.Result second = compiler.compile(root,out);
        assertNotEquals(first.revision,second.revision); assertTrue(Files.exists(first.directory.resolve("voxelcore.mcpack")));
    }
    @Test public void rejectsGeneratedAssetOverrides() throws Exception {
        Path root = fixture(); write(root.resolve("demo/bedrock/resource_pack/manifest.json"), "{}");
        try { new BedrockPackCompiler().compile(root,temp.newFolder().toPath()); fail(); }
        catch(java.io.IOException expected) { assertTrue(expected.getMessage().contains("generated")); }
    }
    @Test public void requiresRealGeyserDirectory() throws Exception {
        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(fixture(),temp.newFolder().toPath());
        try { new GeyserPackPublisher().publish(result,temp.getRoot().toPath().resolve("missing")); fail(); }
        catch(java.io.IOException expected) { assertTrue(expected.getMessage().contains("does not exist")); }
    }
    @Test public void sharesIdenticalSelectors() throws Exception {
        Path root = fixture();
        Path items = root.resolve("demo/content/items.yml");
        String content = new String(Files.readAllBytes(items), StandardCharsets.UTF_8);
        content += "  duplicate:\n    extends: token\n";
        Files.write(items, content.getBytes(StandardCharsets.UTF_8));
        assertEquals(1,new BedrockPackCompiler().compile(root,temp.newFolder().toPath()).mappedItems);
    }
    @Test public void authoredAssetsAreIncludedAndRemovedOnRebuild() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        Path asset = root.resolve("demo/bedrock/resource_pack/texts/en_US.lang");
        write(asset,"item.demo.name=Demo");
        BedrockPackCompiler compiler = new BedrockPackCompiler();
        BedrockPackCompiler.Result first = compiler.compile(root,out);
        try (ZipFile zip = new ZipFile(first.directory.resolve("voxelcore.mcpack").toFile())) {
            assertNotNull(zip.getEntry("texts/en_US.lang"));
        }
        Files.delete(asset);
        BedrockPackCompiler.Result second = compiler.compile(root,out);
        try (ZipFile zip = new ZipFile(second.directory.resolve("voxelcore.mcpack").toFile())) {
            assertNull(zip.getEntry("texts/en_US.lang"));
        }
    }
    @Test public void automaticallyMapsGeneratedModelAndCachesIcon() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        Path items = root.resolve("demo/content/items.yml");
        String content = new String(Files.readAllBytes(items), StandardCharsets.UTF_8);
        content = content.replace("    properties:\n      bedrock:\n        texture: demo:item/token\n", "");
        write(items, content);
        write(root.resolve("demo/assets/demo/models/item/token.json"),
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:item/token\"}}");
        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(root,out);
        assertEquals(1,result.mappedItems);
        try (java.util.stream.Stream<Path> files = Files.list(out.resolve("icon-cache"))) { assertEquals(1,files.count()); }
        assertEquals(result.revision,new BedrockPackCompiler().compile(root,out).revision);
    }
    @Test public void mapsRenderableAbstractUiItems() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        Path items = root.resolve("demo/content/items.yml");
        String content = new String(Files.readAllBytes(items), StandardCharsets.UTF_8);
        content += "  menu_icon:\n    abstract: true\n    material: minecraft:paper\n"
                + "    render:\n      model: demo:ui/menu_icon\n"
                + "    properties:\n      bedrock:\n        texture: demo:item/token\n";
        write(items, content);
        BedrockPackCompiler.Result built = new BedrockPackCompiler().compile(root, out);
        assertEquals(2, built.mappedItems);
        JsonObject mappings = JsonParser.parseString(new String(
                Files.readAllBytes(built.directory.resolve("voxelcore-items.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray paper = mappings.getAsJsonObject("items").getAsJsonArray("minecraft:paper");
        assertEquals(2, paper.size());
        boolean abstractMapped = false;
        for (JsonElement entry : paper) {
            if ("demo:ui/menu_icon".equals(entry.getAsJsonObject().get("model").getAsString()))
                abstractMapped = true;
        }
        assertTrue("Abstract GUI items must be mapped for Bedrock inventory rendering", abstractMapped);
    }
    @Test public void ignoresUnrelatedJavaAssetCollisions() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        write(root.resolve("other/pack.yml"), "schema: 1\nnamespace: other\n");
        for (String path : new String[]{"font/default.json", "lang/en_us.json", "atlases/items.json", "blockstates/stone.json"}) {
            write(root.resolve("demo/assets/minecraft/" + path), "{\"a\":1}");
            write(root.resolve("other/assets/minecraft/" + path), "{\"b\":2}");
        }
        assertEquals(1, new BedrockPackCompiler().compile(root,out).mappedItems);
    }
    @Test public void unrelatedConflictingModelsDoNotAbortBuild() throws Exception {
        Path root = fixture();
        write(root.resolve("other/pack.yml"), "schema: 1\nnamespace: other\n");
        write(root.resolve("demo/assets/minecraft/models/item/test.json"), "{\"parent\":\"demo:a\"}");
        write(root.resolve("other/assets/minecraft/models/item/test.json"), "{\"parent\":\"demo:b\"}");
        assertEquals(1, new BedrockPackCompiler().compile(root,temp.newFolder().toPath()).mappedItems);
    }
    private static void write(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent()); Files.write(path,text.getBytes(StandardCharsets.UTF_8));
    }
}
