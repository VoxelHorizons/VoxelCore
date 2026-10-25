package org.voxelhorizons.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Java menu backgrounds ({@code gui: true} UI glyphs) for Bedrock chest screens.
 *
 * <p>On Java a menu background is a font glyph in the inventory title, pulled to the panel edge
 * with negative spacing ({@code &f:offset_-8::shop_menu:}). Bedrock cannot draw a screen-sized
 * font glyph, but Geyser passes the title through to the chest screen as
 * {@code $container_title}, still holding the glyph's private-use character. The pack therefore
 * overrides the Bedrock chest screen: each background becomes an image control in the chest panel
 * that is only visible while the title contains its character, and the default title label is
 * hidden for those menus (it would print the glyph and spacing characters as boxes).
 */
final class BedrockChestGui {
    private BedrockChestGui() {}

    static final String SCREEN_PATH = "ui/chest_screen.json";
    static final String TEXTURE_DIR = "textures/voxelcore/gui/";
    private static final String NAMESPACE = "chest";
    private static final String TITLE = "$voxelcore_title";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * Java draws a 176px-wide container panel and places the title at (8, 6); the Bedrock chest
     * top half starts one pixel in and seven down from that. With the documented
     * {@code :offset_-8:} prefix a background's left edge sits on the panel's left edge, so it
     * is centred on the panel's middle (88) here, and its top is TOP_BASE - ascent. Measured in a
     * live test (skills menu): the 5 - ascent / centre + 1 placement used by another converter
     * drew the art one GUI pixel right and one low (doubled left outline, first slot row's top
     * border covered), so both are one pixel less.
     */
    static final int PANEL_CENTRE = 88;
    static final int TOP_BASE = 4;

    /** One background: its title character, Bedrock texture and on-screen size in GUI pixels. */
    static final class Background {
        final UiGlyphDefinition glyph;
        final String texture;
        final int width, height;
        Background(UiGlyphDefinition glyph, String texture, int width, int height) {
            this.glyph = glyph; this.texture = texture; this.width = width; this.height = height;
        }
        String control() { return "voxelcore_gui_" + Integer.toHexString(glyph.codePoint()); }
    }

    static int append(Path contentRoot, Path allocationManifest, Map<String, byte[]> javaAssets,
                      Map<String, byte[]> bedrockAssets, List<String> skipped) throws IOException {
        UiGlyphRegistry glyphs = new UiGlyphLoader().load(contentRoot, allocationManifest, false);
        return append(glyphs.entries().values(), javaAssets, bedrockAssets, skipped);
    }

    static int append(Collection<UiGlyphDefinition> glyphs, Map<String, byte[]> javaAssets,
                      Map<String, byte[]> bedrockAssets, List<String> skipped) throws IOException {
        if (bedrockAssets.containsKey(SCREEN_PATH)) {
            skipped.add("menu backgrounds: an authored " + SCREEN_PATH + " already exists; Bedrock menu backgrounds were not generated");
            return 0;
        }
        List<Background> backgrounds = new ArrayList<Background>();
        for (UiGlyphDefinition glyph : glyphs) {
            if (!glyph.gui()) continue;
            String label = "menu background " + glyph.id();
            String[] ref = glyph.texture().split(":", 2);
            byte[] source = javaAssets.get("assets/" + ref[0] + "/textures/" + ref[1] + ".png");
            if (source == null) { skipped.add(label + ": texture " + glyph.texture() + " not found"); continue; }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
            if (image == null) { skipped.add(label + ": unreadable bitmap"); continue; }
            // Java scales the bitmap to the provider height (scale_ratio), keeping its aspect.
            int height = glyph.scaleRatio();
            int width = Math.max(1, (int) Math.round((double) image.getWidth() * height / image.getHeight()));
            String texture = TEXTURE_DIR + Integer.toHexString(glyph.codePoint());
            bedrockAssets.put(texture + ".png", source);
            backgrounds.add(new Background(glyph, texture, width, height));
        }
        if (backgrounds.isEmpty()) return 0;
        bedrockAssets.put(SCREEN_PATH, chestScreen(backgrounds));
        return backgrounds.size();
    }

    /**
     * Bedrock UI expression: true while the title lacks the glyph's character (removing it
     * leaves the title unchanged).
     */
    static String lacks(UiGlyphDefinition glyph) {
        return "((" + TITLE + " - '" + glyph.character() + "') = " + TITLE + ")";
    }

    static byte[] chestScreen(List<Background> backgrounds) {
        JsonObject root = new JsonObject();
        root.addProperty("namespace", NAMESPACE);

        // Vanilla chest title, hidden while any background's character is in the title.
        StringBuilder plain = new StringBuilder();
        for (Background background : backgrounds) {
            if (plain.length() > 0) plain.append(" and ");
            plain.append(lacks(background.glyph));
        }
        JsonObject label = new JsonObject();
        label.addProperty("type", "label");
        label.addProperty("layer", 2);
        label.add("offset", pair(7, -1));
        JsonArray labelSize = new JsonArray(); labelSize.add("90%"); labelSize.add("default");
        label.add("size", labelSize);
        label.addProperty("anchor_from", "top_left");
        label.addProperty("anchor_to", "top_left");
        label.addProperty("text", "$container_title");
        label.addProperty("color", "$title_text_color");
        label.addProperty("localize", "$localize_title");
        label.addProperty("enable_profanity_filter", true);
        label.addProperty(TITLE, "$container_title");
        label.addProperty("visible", "(" + plain + ")");
        root.add("chest_label", label);

        JsonArray controls = new JsonArray();
        for (Background background : backgrounds) {
            JsonObject image = new JsonObject();
            image.addProperty("type", "image");
            image.addProperty("texture", background.texture);
            image.add("size", pair(background.width, background.height));
            image.addProperty("keep_ratio", false);
            image.add("offset", pair(background.width / 2 - PANEL_CENTRE, TOP_BASE - background.glyph.ascent()));
            image.addProperty("anchor_from", "top_middle");
            image.addProperty("anchor_to", "top_middle");
            image.addProperty("layer", 4);
            image.addProperty(TITLE, "$container_title");
            image.addProperty("visible", "(not " + lacks(background.glyph) + ")");
            root.add(background.control(), image);
            JsonObject reference = new JsonObject();
            reference.add(background.control() + "@" + NAMESPACE + "." + background.control(), new JsonObject());
            controls.add(reference);
        }
        // Geyser opens 1-3 row menus as a small chest and 4-6 rows as a large one.
        for (String panel : new String[]{"small_chest_panel_top_half", "large_chest_panel_top_half"}) {
            JsonObject insert = new JsonObject();
            insert.addProperty("array_name", "controls");
            insert.addProperty("operation", "insert_front");
            insert.add("value", controls.deepCopy());
            JsonArray modifications = new JsonArray(); modifications.add(insert);
            JsonObject half = new JsonObject(); half.add("modifications", modifications);
            root.add(panel, half);
        }
        return JSON.toJson(root).getBytes(StandardCharsets.UTF_8);
    }

    private static JsonArray pair(int a, int b) {
        JsonArray out = new JsonArray(); out.add(a); out.add(b); return out;
    }
}
