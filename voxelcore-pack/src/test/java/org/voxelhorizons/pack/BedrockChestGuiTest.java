package org.voxelhorizons.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import org.voxelhorizons.content.ContentID;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.*;

public class BedrockChestGuiTest {
    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xffaa0000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static UiGlyphDefinition glyph(String name, int scale, int ascent, boolean gui, int codePoint) {
        return new UiGlyphDefinition(ContentID.parse("voxel:" + name, "voxel"), "voxel:ui/" + name,
                scale, ascent, 0, gui, codePoint);
    }

    @Test public void menuBackgroundsBecomeChestScreenImagesKeyedByTheirTitleCharacter() throws Exception {
        Map<String, byte[]> java = new TreeMap<String, byte[]>();
        java.put("assets/voxel/textures/ui/shop.png", png(176, 222));
        java.put("assets/voxel/textures/ui/rank.png", png(30, 9));
        java.put(BedrockChestGui.CONTAINER_TEXTURE, png(256, 256));
        Map<String, byte[]> bedrock = new TreeMap<String, byte[]>();
        List<String> skipped = new ArrayList<String>();
        UiGlyphDefinition shop = glyph("shop", 111, 13, true, 0xE2A1);
        int count = BedrockChestGui.append(Arrays.asList(shop, glyph("rank", 9, 8, false, 0xE2A2)), java, bedrock, skipped);
        assertEquals("Only gui: true glyphs are menu backgrounds", 1, count);
        assertTrue(skipped.isEmpty());
        assertNotNull(bedrock.get("textures/voxelcore/gui/e2a1.png"));
        JsonObject screen = JsonParser.parseString(new String(bedrock.get(BedrockChestGui.SCREEN_PATH), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("chest", screen.get("namespace").getAsString());
        JsonObject image = screen.getAsJsonObject("voxelcore_gui_e2a1");
        assertEquals("textures/voxelcore/gui/e2a1", image.get("texture").getAsString());
        assertEquals("Scaled to scale_ratio like Java", "[88,111]", image.get("size").toString());
        assertEquals("Left edge on the panel edge (offset -8), top from the ascent", "[-44,-9]", image.get("offset").toString());
        String character = new String(Character.toChars(0xE2A1));
        assertEquals("(not (($voxelcore_title - '" + character + "') = $voxelcore_title))", image.get("visible").getAsString());
        assertEquals("$container_title", image.get("$voxelcore_title").getAsString());
        assertEquals("Default title hidden for menu backgrounds", "(($voxelcore_title - '" + character + "') = $voxelcore_title)",
                screen.getAsJsonObject("chest_label").get("visible").getAsString().replaceAll("^\\((.*)\\)$", "$1"));
        for (String panel : new String[]{"small_chest_panel_top_half", "large_chest_panel_top_half"})
            assertEquals("[{\"voxelcore_gui_e2a1@chest.voxelcore_gui_e2a1\":{}}]", screen.getAsJsonObject(panel)
                    .getAsJsonArray("modifications").get(0).getAsJsonObject().getAsJsonArray("value").toString());
    }

    @Test public void fullWidthBackgroundIsCentredAndMissingTexturesAreReported() throws Exception {
        Map<String, byte[]> java = new TreeMap<String, byte[]>();
        java.put("assets/voxel/textures/ui/full.png", png(176, 166));
        java.put(BedrockChestGui.CONTAINER_TEXTURE, png(256, 256));
        Map<String, byte[]> bedrock = new TreeMap<String, byte[]>();
        List<String> skipped = new ArrayList<String>();
        BedrockChestGui.append(Arrays.asList(glyph("full", 166, 13, true, 0xE2B0), glyph("gone", 166, 13, true, 0xE2B1)),
                java, bedrock, skipped);
        JsonObject screen = JsonParser.parseString(new String(bedrock.get(BedrockChestGui.SCREEN_PATH), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("[0,-9]", screen.getAsJsonObject("voxelcore_gui_e2b0").get("offset").toString());
        assertFalse(screen.has("voxelcore_gui_e2b1"));
        assertEquals(1, skipped.size());
        assertTrue(skipped.get(0).contains("voxel:gone"));
    }

    @Test public void javaContainerTextureReplacesBedrockFrameOnlyForMenuBackgrounds() throws Exception {
        Map<String, byte[]> java = new TreeMap<String, byte[]>();
        java.put("assets/voxel/textures/ui/full.png", png(176, 166));
        java.put(BedrockChestGui.CONTAINER_TEXTURE, png(512, 512)); // 2x retexture
        Map<String, byte[]> bedrock = new TreeMap<String, byte[]>();
        List<String> skipped = new ArrayList<String>();
        BedrockChestGui.append(Arrays.asList(glyph("full", 166, 13, true, 0xE2B0)), java, bedrock, skipped);
        assertTrue(skipped.isEmpty());
        assertNotNull(bedrock.get("textures/voxelcore/gui/generic_54.png"));
        JsonObject screen = JsonParser.parseString(new String(bedrock.get(BedrockChestGui.SCREEN_PATH), StandardCharsets.UTF_8)).getAsJsonObject();
        String character = new String(Character.toChars(0xE2B0));
        String lacks = "(($voxelcore_title - '" + character + "') = $voxelcore_title)";
        for (String name : new String[]{"small_chest_panel", "large_chest_panel"}) {
            com.google.gson.JsonArray root = null;
            for (com.google.gson.JsonElement control : screen.getAsJsonObject(name).getAsJsonArray("controls"))
                if (control.getAsJsonObject().has("root_panel@common.root_panel"))
                    root = control.getAsJsonObject().getAsJsonObject("root_panel@common.root_panel").getAsJsonArray("controls");
            assertNotNull(name, root);
            JsonObject frame = root.get(0).getAsJsonObject().getAsJsonObject("common_panel@common.common_panel");
            assertEquals("Bedrock frame only without a background", "(" + lacks + ")", frame.get("visible").getAsString());
            JsonObject top = root.get(1).getAsJsonObject().getAsJsonObject("voxelcore_container_top");
            assertEquals("(not (" + lacks + "))", top.get("visible").getAsString());
            int rows = name.startsWith("large") ? 6 : 3;
            assertEquals("[176," + (rows * 18 + 17) + "]", top.get("size").toString());
            assertEquals("UV in texture pixels (2x)", "[352.0," + (rows * 18 + 17) * 2.0 + "]", top.get("uv_size").toString());
            JsonObject inventory = root.get(2).getAsJsonObject().getAsJsonObject("voxelcore_container_inventory");
            assertEquals("[0.0,252.0]", inventory.get("uv").toString());
            assertEquals("bottom_left", inventory.get("anchor_from").getAsString());
        }
    }

    @Test public void withoutContainerTextureBedrockFrameIsKept() throws Exception {
        Map<String, byte[]> java = new TreeMap<String, byte[]>();
        java.put("assets/voxel/textures/ui/full.png", png(176, 166));
        Map<String, byte[]> bedrock = new TreeMap<String, byte[]>();
        List<String> skipped = new ArrayList<String>();
        BedrockChestGui.append(Arrays.asList(glyph("full", 166, 13, true, 0xE2B0)), java, bedrock, skipped);
        JsonObject screen = JsonParser.parseString(new String(bedrock.get(BedrockChestGui.SCREEN_PATH), StandardCharsets.UTF_8)).getAsJsonObject();
        assertFalse(screen.has("small_chest_panel"));
        assertEquals(1, skipped.size());
    }

    @Test public void authoredChestScreenIsNeverOverwritten() throws Exception {
        Map<String, byte[]> bedrock = new TreeMap<String, byte[]>();
        bedrock.put(BedrockChestGui.SCREEN_PATH, "{}".getBytes(StandardCharsets.UTF_8));
        List<String> skipped = new ArrayList<String>();
        assertEquals(0, BedrockChestGui.append(Arrays.asList(glyph("full", 166, 13, true, 0xE2B0)),
                new TreeMap<String, byte[]>(), bedrock, skipped));
        assertEquals("{}", new String(bedrock.get(BedrockChestGui.SCREEN_PATH), StandardCharsets.UTF_8));
        assertEquals(1, skipped.size());
    }
}
