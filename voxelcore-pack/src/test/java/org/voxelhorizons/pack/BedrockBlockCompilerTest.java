package org.voxelhorizons.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.block.BlockAllocation;
import org.voxelhorizons.content.block.BlockAllocationRegistry;
import org.voxelhorizons.content.block.BlockAllocationStore;
import org.voxelhorizons.content.block.BlockCarrierStates;
import org.voxelhorizons.content.block.BlockMethod;
import org.voxelhorizons.content.load.ContentLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

public final class BedrockBlockCompilerTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private Path fixture() throws Exception {
        Path root = temp.newFolder().toPath();
        write(root.resolve("demo/pack.yml"), "schema: 1\nnamespace: demo\n");
        write(root.resolve("demo/content/blocks.yml"),
                "blocks:\n"
              + "  ruby_ore:\n    display_name: '&fRuby Ore'\n    texture: demo:block/ore/ruby_ore\n"
              + "    hardness: 3.0\n    blast_resistance: 3.0\n"
              + "    drop: demo:ruby\n    silk_touch: demo:ruby_ore\n"
              + "  deepslate_ruby_ore:\n    display_name: '&fDeepslate Ruby Ore'\n"
              + "    texture: demo:block/ore/deepslate_ruby_ore\n"
              + "    hardness: 12.0\n    blast_resistance: 3.0\n"
              + "    drop: demo:ruby\n    silk_touch: demo:deepslate_ruby_ore\n");
        png(root.resolve("demo/assets/demo/textures/block/ore/ruby_ore.png"), 0xffe00024);
        png(root.resolve("demo/assets/demo/textures/block/ore/deepslate_ruby_ore.png"), 0xffb40033);
        return root;
    }

    private Path manifest(Path root) throws Exception {
        Path dir = temp.newFolder().toPath();
        Path state = dir.resolve("block-allocations.yml");
        BlockAllocationRegistry allocations = BlockAllocationRegistry.reconcile(
                new ContentLoader().loadDefinitions(root).blocks(), null, true);
        new BlockAllocationStore(state).save(allocations);
        return dir.resolve("glyph-allocations.yml");
    }

    @Test public void twoOreDefinitionsProduceExactGeyserStateOverridesAndTerrainTextures() throws Exception {
        Path root = fixture(), glyphs = manifest(root), out = temp.newFolder().toPath();
        BlockAllocationRegistry allocations = new BlockAllocationStore(
                glyphs.resolveSibling("block-allocations.yml")).load();
        assertEquals(BlockMethod.MUSHROOM, allocations.get(ContentID.of("demo", "ruby_ore")).get().method());
        assertEquals(BlockMethod.MUSHROOM, allocations.get(ContentID.of("demo", "deepslate_ruby_ore")).get().method());

        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(root, out, glyphs);
        assertEquals(2, result.mappedBlocks);
        JsonObject mapping = json(Files.readAllBytes(result.directory.resolve("voxelcore-blocks.json")));
        assertEquals(1, mapping.get("format_version").getAsInt());
        JsonObject mappedBlocks = mapping.getAsJsonObject("blocks");
        assertFalse("New blocks must not allocate vanilla note pitch combinations",
                mappedBlocks.has("minecraft:note_block"));
        JsonObject brown = mappedBlocks.getAsJsonObject("minecraft:brown_mushroom_block");
        assertTrue(brown.get("only_override_states").getAsBoolean());
        assertFalse(brown.get("included_in_creative_inventory").getAsBoolean());
        JsonObject overrides = brown.getAsJsonObject("state_overrides");
        assertEquals(2, overrides.size());
        for (String id : new String[]{"ruby_ore", "deepslate_ruby_ore"}) {
            BlockAllocation allocation = allocations.get(ContentID.of("demo", id)).get();
            String state = BlockCarrierStates.state(allocation, true);
            JsonObject entry = overrides.getAsJsonObject(BlockCarrierStates.properties(state));
            assertNotNull("Missing state mapping for " + state, entry);
            assertTrue(entry.get("unit_cube").getAsBoolean());
            assertFalse("The Paper server must handle placement", entry.get("place_air").getAsBoolean());
            assertTrue(entry.getAsJsonObject("material_instances").has("*"));
        }
        try (ZipFile pack = new ZipFile(result.directory.resolve("voxelcore.mcpack").toFile())) {
            assertNotNull(pack.getEntry("textures/terrain_texture.json"));
            JsonObject terrain = json(readAll(pack.getInputStream(pack.getEntry("textures/terrain_texture.json"))));
            assertEquals(2, terrain.getAsJsonObject("texture_data").size());
            assertEquals(2, pack.stream().filter(e -> e.getName().startsWith("textures/voxelcore/b_")).count());
        }
        BedrockPackCompiler.Result repeat = new BedrockPackCompiler().compile(root, out, glyphs);
        assertEquals("The block mapping must be deterministic", result.revision, repeat.revision);
    }


    @Test public void generatedBlockModelsBecomeInventoryIconsAndGeyserItemMappings() throws Exception {
        Path root = fixture(), glyphs = manifest(root), out = temp.newFolder().toPath();
        BedrockPackCompiler.Result first = new BedrockPackCompiler().compile(root, out, glyphs);
        assertEquals("Both implicit block items must be exported as Bedrock inventory icons", 2, first.mappedItems);
        assertEquals("Placed blocks still have their own mappings", 2, first.mappedBlocks);

        JsonObject mapping = json(Files.readAllBytes(first.directory.resolve("voxelcore-items.json")));
        JsonObject items = mapping.getAsJsonObject("items");
        com.google.gson.JsonArray paper = items.getAsJsonArray("minecraft:paper");
        assertNotNull("The implicit block items use the standard stackable carrier", paper);
        assertEquals(2, paper.size());

        try (ZipFile pack = new ZipFile(first.directory.resolve("voxelcore.mcpack").toFile())) {
            JsonObject atlas = json(readAll(pack.getInputStream(pack.getEntry("textures/item_texture.json"))))
                    .getAsJsonObject("texture_data");
            java.util.Set<String> uniqueKeys = new java.util.HashSet<String>();
            Map<String,Integer> colours = new LinkedHashMap<String,Integer>();
            colours.put("ruby_ore", 0xffe00024);
            colours.put("deepslate_ruby_ore", 0xffb40033);
            for (Map.Entry<String,Integer> ore : colours.entrySet()) {
                String model = "demo:block/" + ore.getKey();
                JsonObject entry = null;
                for (com.google.gson.JsonElement element : paper) {
                    JsonObject candidate = element.getAsJsonObject();
                    if (model.equals(candidate.get("model").getAsString())) entry = candidate;
                }
                assertNotNull("Missing Geyser item mapping for " + model, entry);
                assertEquals("definition", entry.get("type").getAsString());
                String iconKey = entry.getAsJsonObject("bedrock_options").get("icon").getAsString();
                assertTrue("Distinct blocks must get distinct atlas entries", uniqueKeys.add(iconKey));
                JsonObject texture = atlas.getAsJsonObject(iconKey);
                assertNotNull("Missing Bedrock icon atlas entry " + iconKey, texture);
                String iconPath = texture.get("textures").getAsString() + ".png";
                assertNotNull("Missing generated 2D icon PNG " + iconPath, pack.getEntry(iconPath));
                BufferedImage icon = ImageIO.read(new java.io.ByteArrayInputStream(
                        readAll(pack.getInputStream(pack.getEntry(iconPath)))));
                assertEquals("Block items use the same rendered 64px preview pipeline as furniture",64,icon.getWidth());
                assertEquals(64,icon.getHeight());
                boolean coloured = false, transparent = false;
                for (int y = 0; y < icon.getHeight(); y++) for (int x = 0; x < icon.getWidth(); x++) {
                    int pixel = icon.getRGB(x, y);
                    if ((pixel >>> 24) == 0) transparent = true;
                    else {
                        assertEquals("This block preview must contain its own ore texture, not paper",
                                ore.getValue().intValue(), pixel);
                        coloured = true;
                    }
                }
                assertTrue("Icon must contain visible block geometry",coloured);
                assertTrue("The rendered cube preview retains transparent background",transparent);
            }
        }
        BedrockPackCompiler.Result rebuilt = new BedrockPackCompiler().compile(root, out, glyphs);
        assertEquals("Generating block icons must not randomize pack revisions", first.revision, rebuilt.revision);
        assertArrayEquals(Files.readAllBytes(first.directory.resolve("voxelcore-items.json")),
                Files.readAllBytes(rebuilt.directory.resolve("voxelcore-items.json")));
    }

    @Test public void generatedCubeColumnAndSixFaceBlocksGetIconsWithoutVanillaClientJar() throws Exception {
        Path root = fixture(), glyphs = manifest(root);
        write(root.resolve("demo/content/blocks.yml"),
                "blocks:\n"
                + "  ruby_log:\n    model: cube_column\n    textures:\n"
                + "      side: demo:block/ore/ruby_ore\n"
                + "      top: demo:block/ore/deepslate_ruby_ore\n"
                + "      bottom: demo:block/ore/ruby_ore\n"
                + "  six_face_ore:\n    model: cube\n    textures:\n"
                + "      north: demo:block/ore/ruby_ore\n"
                + "      south: demo:block/ore/ruby_ore\n"
                + "      east: demo:block/ore/ruby_ore\n"
                + "      west: demo:block/ore/ruby_ore\n"
                + "      up: demo:block/ore/deepslate_ruby_ore\n"
                + "      down: demo:block/ore/ruby_ore\n");
        // Allocate against this modified content snapshot, not the original fixture.
        glyphs = manifest(root);
        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(root, temp.newFolder().toPath(), glyphs);
        assertEquals(2,result.mappedItems);
        assertEquals(2,result.mappedBlocks);
        JsonObject mapping = json(Files.readAllBytes(result.directory.resolve("voxelcore-items.json")));
        com.google.gson.JsonArray items = mapping.getAsJsonObject("items").getAsJsonArray("minecraft:paper");
        assertEquals(2,items.size());
        try (ZipFile pack = new ZipFile(result.directory.resolve("voxelcore.mcpack").toFile())) {
            JsonObject textures = json(readAll(pack.getInputStream(pack.getEntry("textures/item_texture.json"))))
                    .getAsJsonObject("texture_data");
            for (com.google.gson.JsonElement item : items) {
                JsonObject def = item.getAsJsonObject();
                assertTrue(def.get("model").getAsString(),
                        def.get("model").getAsString().startsWith("demo:block/"));
                String icon = def.getAsJsonObject("bedrock_options").get("icon").getAsString();
                String texture = textures.getAsJsonObject(icon).get("textures").getAsString();
                assertNotNull(pack.getEntry(texture + ".png"));
            }
        }
    }

    @Test public void authoredItemOverrideForBlockKeepsItsOwnMaterialModelAndIcon() throws Exception {
        Path root = fixture(), glyphs = manifest(root);
        write(root.resolve("demo/content/overrides.yml"),
                "items:\n  ruby_ore:\n    material: minecraft:stick\n    render:\n"
                + "      model: demo:item/custom_ore\n");
        write(root.resolve("demo/assets/demo/models/item/custom_ore.json"),
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:block/ore/ruby_ore\"}}");
        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(root,temp.newFolder().toPath(),glyphs);
        assertEquals(2,result.mappedItems);
        assertEquals(2,result.mappedBlocks);
        JsonObject mappings = json(Files.readAllBytes(result.directory.resolve("voxelcore-items.json")))
                .getAsJsonObject("items");
        assertEquals("Explicit item definition must win over the implicit block item",
                "demo:item/custom_ore",mappings.getAsJsonArray("minecraft:stick").get(0).getAsJsonObject()
                        .get("model").getAsString());
        assertEquals("The remaining implicit block item retains its paper carrier",
                "demo:block/deepslate_ruby_ore",mappings.getAsJsonArray("minecraft:paper").get(0)
                        .getAsJsonObject().get("model").getAsString());
    }

    @Test public void originalNoteAllocationsStayMappedWithoutReassigningWorldBlocks() throws Exception {
        Path root = fixture(), glyphs = manifest(root);
        Path allocationsPath = glyphs.resolveSibling("block-allocations.yml");
        Map<ContentID, BlockAllocation> existing = new LinkedHashMap<ContentID, BlockAllocation>();
        existing.put(ContentID.of("demo", "ruby_ore"), new BlockAllocation(BlockMethod.SOLID, 0, true));
        existing.put(ContentID.of("demo", "deepslate_ruby_ore"), new BlockAllocation(BlockMethod.MUSHROOM, 11, true));
        new BlockAllocationStore(allocationsPath).save(new BlockAllocationRegistry(existing));
        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(root, temp.newFolder().toPath(), glyphs);
        JsonObject mappings = json(Files.readAllBytes(result.directory.resolve("voxelcore-blocks.json")))
                .getAsJsonObject("blocks");
        assertTrue(mappings.has("minecraft:note_block"));
        assertTrue(mappings.getAsJsonObject("minecraft:note_block")
                .getAsJsonObject("state_overrides").has("instrument=harp,note=0,powered=false"));
    }

    @Test public void missingAllocationManifestFailsClosedInsteadOfExportingUnmappedBlocks() throws Exception {
        Path root = fixture(), glyphs = temp.newFolder().toPath().resolve("glyph-allocations.yml");
        try {
            new BedrockPackCompiler().compile(root, temp.newFolder().toPath(), glyphs);
            fail("Missing manifest must not silently produce vanilla-looking Bedrock blocks");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("block-allocations.yml"));
        }
    }

    @Test public void rejectedBlockTextureNeverCreatesBrokenGeyserStateMapping() throws Exception {
        Path root = fixture(), glyphs = manifest(root);
        Files.delete(root.resolve("demo/assets/demo/textures/block/ore/ruby_ore.png"));
        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(root, temp.newFolder().toPath(), glyphs);
        assertEquals(1, result.mappedBlocks);
        String unsupported = new String(Files.readAllBytes(result.directory.resolve("unsupported-items.txt")),
                StandardCharsets.UTF_8);
        assertTrue(unsupported, unsupported.contains("block demo:ruby_ore: missing Java block texture"));
        JsonObject blocks = json(Files.readAllBytes(result.directory.resolve("voxelcore-blocks.json")))
                .getAsJsonObject("blocks");
        assertEquals(1, blocks.getAsJsonObject("minecraft:brown_mushroom_block")
                .getAsJsonObject("state_overrides").size());
    }

    private static byte[] readAll(java.io.InputStream input) throws java.io.IOException {
        try (java.io.InputStream in = input; java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }

    private static JsonObject json(byte[] data) {
        return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static void png(Path path, int color) throws Exception {
        Files.createDirectories(path.getParent());
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) image.setRGB(x, y, color);
        ImageIO.write(image, "png", path.toFile());
    }
    private static void write(Path path, String value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, value.getBytes(StandardCharsets.UTF_8));
    }
}
