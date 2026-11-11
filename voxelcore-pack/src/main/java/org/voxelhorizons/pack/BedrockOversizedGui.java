package org.voxelhorizons.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.voxelhorizons.content.item.ItemDefinition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Experimental Classic-UI chest projection for Java's oversized_in_gui items.
 *
 * Bedrock has no per-item equivalent to Java's item-model flag. Instead of enlarging
 * every slot, draw a second, larger inventory_item_renderer for a *matching* stack.
 * The original slot (including its native click/drag handling) is left intact.
 *
 * The match uses the chest's per-slot #hover_text collection binding. This is
 * deliberately best-effort: the binding and the clipping/layering behaviour must
 * be verified on a real Bedrock client before this can be considered parity.
 */
final class BedrockOversizedGui {
    private BedrockOversizedGui() {}

    static final class Entry {
        final String item, match;
        final int width, height, offsetX, offsetY;

        Entry(String item, String match, int width, int height, int offsetX, int offsetY) {
            this.item = item;
            this.match = match;
            this.width = width;
            this.height = height;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    static Entry entry(ItemDefinition item, Map<?, ?> settings, BedrockIconRenderer renderer,
                       String model) throws IOException {
        if (!item.render().oversizedInGui()) return null;
        Object raw = settings.get("oversized_gui");
        if (Boolean.FALSE.equals(raw)) return null;
        if (raw != null && !(raw instanceof Map))
            throw new IOException("properties.bedrock.oversized_gui must be a map or false");
        Map<?, ?> config = raw == null ? java.util.Collections.emptyMap() : (Map<?, ?>) raw;

        double[] scale = renderer.guiScale(model);
        int width = integer(config, "width", (int) Math.round(16 * Math.abs(scale[0])), 1, 128);
        int height = integer(config, "height", (int) Math.round(16 * Math.abs(scale[1])), 1, 128);
        int offsetX = integer(config, "offset_x", 0, -64, 64);
        int offsetY = integer(config, "offset_y", 0, -64, 64);

        // A regular 16x16 icon cannot overflow; do not add an unnecessary second renderer.
        if (width <= 16 && height <= 16) return null;

        Object explicit = config.get("match_text");
        if (explicit != null && !(explicit instanceof String))
            throw new IOException("oversized_gui.match_text must be a string");
        String match = explicit == null ? item.displayName() : (String) explicit;
        if (match != null) {
            // Legacy colour codes are not part of the displayed item name.
            match = match.replaceAll("(?i)[&§][0-9a-fk-or]", "").trim();
        }
        if (match == null || match.isEmpty() || match.length() > 80
                || match.indexOf('\'') >= 0 || match.indexOf('\n') >= 0)
            throw new IOException("oversized GUI needs a unique, non-empty display_name "
                    + "or properties.bedrock.oversized_gui.match_text (no apostrophes)");
        return new Entry(item.id().toString(), match, width, height, offsetX, offsetY);
    }

    private static int integer(Map<?, ?> config, String key, int fallback, int min, int max) throws IOException {
        Object value = config.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number) || ((Number) value).doubleValue() != ((Number) value).intValue())
            throw new IOException("oversized_gui." + key + " must be an integer");
        int result = ((Number) value).intValue();
        if (result < min || result > max)
            throw new IOException("oversized_gui." + key + " must be between " + min + " and " + max);
        return result;
    }

    static int append(List<Entry> candidates, Map<String, byte[]> assets, boolean authoredScreen,
                      List<String> unsupported) {
        if (candidates.isEmpty()) return 0;
        if (authoredScreen) {
            unsupported.add("oversized GUI: authored ui/chest_screen.json takes precedence; "
                    + "automatic Classic chest overlays skipped");
            return 0;
        }

        // Two Java variants with the same visible name cannot be distinguished by the
        // Bedrock collection text binding. Refuse conflicting sizes rather than overlap.
        Map<String, Entry> unique = new LinkedHashMap<String, Entry>();
        for (Entry entry : candidates) {
            Entry earlier = unique.get(entry.match);
            if (earlier != null) {
                if (earlier.width != entry.width || earlier.height != entry.height
                        || earlier.offsetX != entry.offsetX || earlier.offsetY != entry.offsetY)
                    unsupported.add(entry.item + ": oversized GUI text '" + entry.match
                            + "' is also used by " + earlier.item
                            + " with different dimensions; first overlay kept");
                continue;
            }
            if (unique.size() >= 64) {
                unsupported.add("oversized GUI: limit of 64 distinct chest overlay matches reached");
                break;
            }
            unique.put(entry.match, entry);
        }

        byte[] previous = assets.get(BedrockChestGui.SCREEN_PATH);
        JsonObject screen = previous == null ? new JsonObject()
                : JsonParser.parseString(new String(previous, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!screen.has("namespace")) screen.addProperty("namespace", "chest");
        JsonArray additions = new JsonArray();
        int index = 0;
        for (Entry entry : unique.values()) {
            String name = "voxelcore_oversized_" + index++;
            JsonObject overlay = new JsonObject();
            overlay.addProperty("type", "panel");
            overlay.add("size", pair(18, 18));
            overlay.addProperty("layer", 25);
            overlay.addProperty("clips_children", false);
            overlay.addProperty("visible", false);

            JsonArray bindings = new JsonArray();
            JsonObject scope = new JsonObject();
            scope.addProperty("binding_type", "collection_details");
            scope.addProperty("binding_collection_name", "container_items");
            bindings.add(scope);
            JsonObject text = new JsonObject();
            text.addProperty("binding_type", "collection");
            text.addProperty("binding_collection_name", "container_items");
            text.addProperty("binding_name", "#hover_text");
            text.addProperty("binding_name_override", "#voxelcore_hover_text");
            text.addProperty("binding_condition", "always");
            bindings.add(text);
            JsonObject condition = new JsonObject();
            condition.addProperty("binding_type", "view");
            condition.addProperty("source_property_name", "(not ((#voxelcore_hover_text - '"
                    + entry.match + "') = #voxelcore_hover_text))");
            condition.addProperty("target_property_name", "#visible");
            bindings.add(condition);
            overlay.add("bindings", bindings);

            JsonObject icon = new JsonObject();
            icon.addProperty("type", "custom");
            icon.addProperty("renderer", "inventory_item_renderer");
            icon.add("size", pair(entry.width, entry.height));
            icon.add("offset", pair(entry.offsetX, entry.offsetY));
            icon.addProperty("anchor_from", "center");
            icon.addProperty("anchor_to", "center");
            icon.addProperty("layer", 1);
            JsonArray rendererBindings = new JsonArray();
            rendererBindings.add(scope.deepCopy());
            JsonObject data = new JsonObject();
            data.addProperty("binding_type", "collection");
            data.addProperty("binding_collection_name", "container_items");
            data.addProperty("binding_name", "#item_renderer_data");
            data.addProperty("binding_condition", "visible");
            rendererBindings.add(data);
            icon.add("bindings", rendererBindings);
            JsonArray controls = new JsonArray();
            JsonObject control = new JsonObject();
            control.add(name + "_icon", icon);
            controls.add(control);
            overlay.add("controls", controls);
            screen.add(name, overlay);

            JsonObject reference = new JsonObject();
            reference.add(name + "@chest." + name, new JsonObject());
            additions.add(reference);
        }

        // This changes only the chest grid, not player inventory/hotbar or other screens.
        // No buttons are inserted: input still hits the existing 18x18 Bedrock slot.
        JsonObject modification = new JsonObject();
        modification.addProperty("array_name", "controls");
        modification.addProperty("operation", "insert_back");
        modification.add("value", additions);
        JsonArray modifications = new JsonArray();
        modifications.add(modification);
        JsonObject slot = new JsonObject();
        slot.addProperty("$item_collection_name", "container_items");
        slot.add("modifications", modifications);
        screen.add("chest_grid_item@common.container_item", slot);

        assets.put(BedrockChestGui.SCREEN_PATH,
                screen.toString().getBytes(StandardCharsets.UTF_8));
        return unique.size();
    }

    private static JsonArray pair(int x, int y) {
        JsonArray out = new JsonArray();
        out.add(x);
        out.add(y);
        return out;
    }
}
