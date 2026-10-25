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
 * hidden for those menus (it would print the glyph and spacing characters as boxes). On those
 * menus Bedrock's grey dialog frame and close button are also replaced by the Java container
 * texture (generic_54), so a retextured, partly transparent container looks as it does on Java.
 * Only the Classic UI chest layout is changed; Bedrock's Pocket UI uses its own screens.
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
     * border covered), so both are one pixel less. A later live test of a large chest with a
     * slot-art background showed its slots 2px above Bedrock's, so the top moved 2px down.
     */
    static final int PANEL_CENTRE = 88;
    static final int TOP_BASE = 6;

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
        // The Java container art (a pack's retextured generic_54, else vanilla) replaces Bedrock's
        // grey dialog frame on those menus, so transparent parts of it look the same as on Java.
        byte[] container = javaAssets.get(CONTAINER_TEXTURE);
        BufferedImage containerImage = container == null ? null : ImageIO.read(new ByteArrayInputStream(container));
        if (containerImage == null) skipped.add("menu backgrounds: " + CONTAINER_TEXTURE + " not found; Bedrock's own chest frame is kept");
        else bedrockAssets.put(CONTAINER_PANEL + ".png", container);
        bedrockAssets.put(SCREEN_PATH, chestScreen(backgrounds,
                containerImage == null ? 0 : containerImage.getWidth() / 256.0));
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
        return chestScreen(backgrounds, 0);
    }

    /** @param containerScale container texture pixels per Java GUI pixel (0 = keep Bedrock's frame) */
    static byte[] chestScreen(List<Background> backgrounds, double containerScale) {
        JsonObject root = new JsonObject();
        root.addProperty("namespace", NAMESPACE);

        // Vanilla chest title, hidden while any background's character is in the title.
        StringBuilder plain = new StringBuilder(); // no background in the title
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
        if (containerScale > 0) {
            String visible = "(not (" + plain + "))";
            root.add("small_chest_panel", chestPanel(3, false, "(" + plain + ")", visible, containerScale));
            root.add("large_chest_panel", chestPanel(6, true, "(" + plain + ")", visible, containerScale));
        }
        return JSON.toJson(root).getBytes(StandardCharsets.UTF_8);
    }

    static final String CONTAINER_TEXTURE = "assets/minecraft/textures/gui/container/generic_54.png";
    static final String CONTAINER_PANEL = TEXTURE_DIR + "generic_54";
    /**
     * Java draws generic_54 in two parts: rows * 18 + 17 pixels for the header and chest rows,
     * then the 96px player inventory from y = 126. Measured in a live test (large chest, 4 screen
     * px per GUI px): Bedrock's chest slots sit 5px lower than Java's in the panel, its inventory
     * rows match Java's when the inventory part is anchored to the panel bottom, but its hotbar
     * has a 1px gap above it instead of Java's 3px. So the top part is drawn 5px down, and the
     * inventory part is cut in four: the rows (to 2px into the gap), the hotbar strip 2px higher,
     * 2px of the footer filling the space that leaves, and the 7px footer at the bottom.
     */
    static final int CONTAINER_TOP_SHIFT = 5;
    static final int HOTBAR_RAISE = 2;

    private static JsonObject containerPart(int v, int height, boolean top, double scale, String visible) {
        return containerPart(v, height, top, top ? CONTAINER_TOP_SHIFT : 0, top ? 2 : 1, scale, visible);
    }

    /** @param offsetY top-anchored: down from the panel top; else up from the panel bottom (negative) */
    private static JsonObject containerPart(int v, int height, boolean top, int offsetY, int layer,
                                            double scale, String visible) {
        JsonObject image = new JsonObject();
        image.addProperty("type", "image");
        image.addProperty("texture", CONTAINER_PANEL);
        image.add("uv", pairD(0, v * scale));
        image.add("uv_size", pairD(176 * scale, height * scale));
        image.add("size", pair(176, height));
        image.addProperty("anchor_from", top ? "top_left" : "bottom_left");
        image.addProperty("anchor_to", top ? "top_left" : "bottom_left");
        image.add("offset", pair(0, offsetY));
        image.addProperty("layer", layer);
        image.addProperty(TITLE, "$container_title");
        image.addProperty("visible", visible);
        return image;
    }

    private static JsonObject named(String name, JsonObject value) {
        JsonObject out = new JsonObject(); out.add(name, value); return out;
    }

    /**
     * Vanilla Bedrock small/large chest panel (Mojang bedrock-samples ui/chest_screen.json) with
     * two changes: the grey dialog frame and close button (common_panel) only show without a
     * menu background, and with one the Java container texture is drawn instead.
     */
    private static JsonObject chestPanel(int rows, boolean large, String plain, String menu, double scale) {
        String half = large ? "large_chest_panel_top_half" : "small_chest_panel_top_half";
        JsonArray rootControls = new JsonArray();
        JsonObject frame = new JsonObject();
        frame.addProperty(TITLE, "$container_title");
        frame.addProperty("visible", plain);
        rootControls.add(named("common_panel@common.common_panel", frame));
        rootControls.add(named("voxelcore_container_top", containerPart(0, rows * 18 + 17, true, scale, menu)));
        // Inventory part (texture y 126..221): rows 126..194, hotbar strip 195..214, footer 215..221.
        rootControls.add(named("voxelcore_container_inventory", containerPart(126, 69, false, -27, 1, scale, menu)));
        rootControls.add(named("voxelcore_container_hotbar", containerPart(195, 20, false, -7 - HOTBAR_RAISE, 2, scale, menu)));
        rootControls.add(named("voxelcore_container_gap", containerPart(215, HOTBAR_RAISE, false, -7, 1, scale, menu)));
        rootControls.add(named("voxelcore_container_footer", containerPart(215, 7, false, 0, 1, scale, menu)));
        JsonArray chestControls = new JsonArray();
        chestControls.add(named(half + "@chest." + half, new JsonObject()));
        chestControls.add(named("inventory_panel_bottom_half_with_label@common.inventory_panel_bottom_half_with_label", new JsonObject()));
        chestControls.add(named("hotbar_grid@common.hotbar_grid_template", new JsonObject()));
        chestControls.add(named("inventory_take_progress_icon_button@common.inventory_take_progress_icon_button", new JsonObject()));
        JsonObject flying = new JsonObject(); flying.addProperty("layer", 15);
        chestControls.add(named("flying_item_renderer@common.flying_item_renderer", flying));
        JsonObject chest = new JsonObject();
        chest.addProperty("type", "panel");
        chest.addProperty("layer", 5);
        chest.add("controls", chestControls);
        rootControls.add(named("chest_panel", chest));
        rootControls.add(named("inventory_selected_icon_button@common.inventory_selected_icon_button", new JsonObject()));
        rootControls.add(named("gamepad_cursor@common.gamepad_cursor_button", new JsonObject()));
        JsonObject rootPanel = new JsonObject();
        if (large) rootPanel.add("size", pair(176, 220));
        rootPanel.addProperty("layer", 1);
        rootPanel.add("controls", rootControls);

        JsonArray controls = new JsonArray();
        controls.add(named("container_gamepad_helpers@common.container_gamepad_helpers", new JsonObject()));
        JsonObject details = new JsonObject(), lock = new JsonObject();
        if (large) {
            details.addProperty("control_name", "@chest.selected_item_details");
            lock.addProperty("control_name", "@common.item_lock_notification");
        }
        controls.add(named("selected_item_details_factory@common.selected_item_details_factory", details));
        controls.add(named("item_lock_notification_factory@common.item_lock_notification_factory", lock));
        controls.add(named("root_panel@common.root_panel", rootPanel));
        JsonObject panel = new JsonObject();
        panel.addProperty("type", "panel");
        panel.add("controls", controls);
        return panel;
    }

    private static JsonArray pairD(double a, double b) {
        JsonArray out = new JsonArray(); out.add(a); out.add(b); return out;
    }

    private static JsonArray pair(int a, int b) {
        JsonArray out = new JsonArray(); out.add(a); out.add(b); return out;
    }
}
