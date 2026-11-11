package org.voxelhorizons.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

public class BedrockOversizedGuiTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void addsSlotBoundRendererWithoutReplacingNativeSlotButton() {
        Map<String, byte[]> assets = new HashMap<String, byte[]>();
        List<String> unsupported = new ArrayList<String>();
        BedrockOversizedGui.Entry on = new BedrockOversizedGui.Entry("demo:on", "On Toggle", 36, 18, 0, 0);
        assertEquals(1, BedrockOversizedGui.append(Arrays.asList(on), assets, false, unsupported));
        assertTrue(unsupported.isEmpty());

        JsonObject root = JsonParser.parseString(
                new String(assets.get(BedrockChestGui.SCREEN_PATH), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("chest", root.get("namespace").getAsString());
        JsonObject overlay = root.getAsJsonObject("voxelcore_oversized_0");
        assertFalse(overlay.get("clips_children").getAsBoolean());
        assertEquals("[18,18]", overlay.getAsJsonArray("size").toString());
        JsonArray bindings = overlay.getAsJsonArray("bindings");
        assertEquals("#hover_text", bindings.get(1).getAsJsonObject().get("binding_name").getAsString());
        assertEquals("container_items", bindings.get(1).getAsJsonObject()
                .get("binding_collection_name").getAsString());
        assertTrue(bindings.get(2).getAsJsonObject().get("source_property_name")
                .getAsString().contains("On Toggle"));

        JsonObject icon = overlay.getAsJsonArray("controls").get(0).getAsJsonObject()
                .getAsJsonObject("voxelcore_oversized_0_icon");
        assertEquals("inventory_item_renderer", icon.get("renderer").getAsString());
        assertEquals("[36,18]", icon.getAsJsonArray("size").toString());
        assertEquals("#item_renderer_data", icon.getAsJsonArray("bindings").get(1)
                .getAsJsonObject().get("binding_name").getAsString());

        JsonObject cell = root.getAsJsonObject("chest_grid_item@common.container_item");
        JsonObject change = cell.getAsJsonArray("modifications").get(0).getAsJsonObject();
        assertEquals("insert_back", change.get("operation").getAsString());
        assertEquals("controls", change.get("array_name").getAsString());
        // There is no button/control replacement; the existing slot remains interactive.
        assertEquals(1, change.getAsJsonArray("value").size());
        assertFalse(root.has("small_chest_grid"));
        assertFalse(root.has("large_chest_grid"));
    }

    @Test public void preservesAuthoredChestUiAndDeduplicatesMatches() {
        Map<String, byte[]> assets = new HashMap<String, byte[]>();
        assets.put(BedrockChestGui.SCREEN_PATH, "{}".getBytes(StandardCharsets.UTF_8));
        List<String> unsupported = new ArrayList<String>();
        BedrockOversizedGui.Entry on = new BedrockOversizedGui.Entry("demo:on", "Toggle", 36, 18, 0, 0);
        assertEquals(0, BedrockOversizedGui.append(Arrays.asList(on), assets, true, unsupported));
        assertEquals("{}", new String(assets.get(BedrockChestGui.SCREEN_PATH), StandardCharsets.UTF_8));
        assertTrue(unsupported.get(0).contains("authored"));

        assets.clear();
        unsupported.clear();
        BedrockOversizedGui.Entry second = new BedrockOversizedGui.Entry("demo:off", "Toggle", 32, 18, 0, 0);
        assertEquals(1, BedrockOversizedGui.append(Arrays.asList(on, second), assets, false, unsupported));
        assertTrue(unsupported.get(0).contains("different dimensions"));
    }

    @Test public void compilesModelGuiScaleIntoChestScreenWithoutChangingMappings() throws Exception {
        Path content = temp.newFolder("content").toPath();
        Path out = temp.newFolder("out").toPath();
        write(content.resolve("demo/pack.yml"), "schema: 1\nnamespace: demo\n");
        write(content.resolve("demo/content/items.yml"),
                "items:\n" +
                "  toggle_on:\n" +
                "    material: minecraft:paper\n" +
                "    display_name: 'Toggle On'\n" +
                "    render:\n" +
                "      model: demo:item/toggle_on\n" +
                "      oversized_in_gui: true\n");
        write(content.resolve("demo/assets/demo/models/item/toggle_on.json"),
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:item/toggle_on\"},"
                        + "\"display\":{\"gui\":{\"scale\":[2.25,1.125,1]}}}");
        Path texture = content.resolve("demo/assets/demo/textures/item/toggle_on.png");
        Files.createDirectories(texture.getParent());
        assertTrue(ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "png", texture.toFile()));

        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(content, out);
        assertEquals(1, result.mappedItems);
        try (ZipFile zip = new ZipFile(result.directory.resolve("voxelcore.mcpack").toFile())) {
            assertNotNull(zip.getEntry("ui/chest_screen.json"));
            JsonObject ui = JsonParser.parseString(new String(readAll(zip, "ui/chest_screen.json"),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject icon = ui.getAsJsonObject("voxelcore_oversized_0").getAsJsonArray("controls").get(0)
                    .getAsJsonObject().getAsJsonObject("voxelcore_oversized_0_icon");
            assertEquals("[36,18]", icon.getAsJsonArray("size").toString());
            assertEquals("container_items", icon.getAsJsonArray("bindings").get(1).getAsJsonObject()
                    .get("binding_collection_name").getAsString());
            assertNotNull(zip.getEntry("textures/item_texture.json"));
        }
        JsonObject mapping = JsonParser.parseString(new String(
                Files.readAllBytes(result.directory.resolve("voxelcore-items.json")),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("demo:item/toggle_on", mapping.getAsJsonObject("items")
                .getAsJsonArray("minecraft:paper").get(0).getAsJsonObject().get("model").getAsString());
    }

    private static byte[] readAll(ZipFile zip, String name) throws Exception {
        java.io.InputStream input = zip.getInputStream(zip.getEntry(name));
        try {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        } finally { input.close(); }
    }

    private static void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }
}
