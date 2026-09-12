package org.voxelhorizons.content.load;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemType;
import org.voxelhorizons.content.item.RawItemDefinition;
import org.voxelhorizons.content.item.RawItemRenderDefinition;
import org.voxelhorizons.content.pack.ContentPack;
import org.voxelhorizons.content.pack.ContentPackDiscovery;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ItemDefinitionParser {
    private static final Set<String> ITEM_KEYS = new HashSet<String>(Arrays.asList(
            "extends", "type", "material", "display_name", "lore", "bound", "render", "properties"
    ));
    private static final Set<String> RENDER_KEYS = new HashSet<String>(Arrays.asList(
            "model", "legacy_custom_model_data"
    ));

    public List<RawItemDefinition> parse(ContentPack pack, Path file) {
        Object loaded;
        try (InputStream input = Files.newInputStream(file)) {
            loaded = ContentPackDiscovery.yaml().load(input);
        } catch (IOException exception) {
            throw new ContentLoadException("Unable to read item definitions: " + file, exception);
        } catch (RuntimeException exception) {
            throw new ContentLoadException("Invalid YAML in item definitions: " + file, exception);
        }
        if (!(loaded instanceof Map)) {
            throw new ContentLoadException("Definition file must be a mapping: " + file);
        }
        Map<?, ?> root = (Map<?, ?>) loaded;
        for (Object key : root.keySet()) {
            if (!"items".equals(key)) {
                throw new ContentLoadException("Unsupported top-level key '" + key + "' in " + file);
            }
        }
        Object itemsValue = root.get("items");
        if (itemsValue == null) return Collections.emptyList();
        if (!(itemsValue instanceof Map)) {
            throw new ContentLoadException("'items' must be a mapping in " + file);
        }

        List<RawItemDefinition> definitions = new ArrayList<RawItemDefinition>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) itemsValue).entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Map)) {
                throw new ContentLoadException("Each item must be a named mapping in " + file);
            }
            String localId = ((String) entry.getKey()).trim();
            ContentID id = ContentID.parse(localId, pack.manifest().namespace());
            if (!id.namespace().equals(pack.manifest().namespace())) {
                throw new ContentLoadException("Item " + id + " in " + file + " must use pack namespace " + pack.manifest().namespace());
            }
            definitions.add(parseItem(pack, file, id, (Map<?, ?>) entry.getValue()));
        }
        return definitions;
    }

    private RawItemDefinition parseItem(ContentPack pack, Path file, ContentID id, Map<?, ?> map) {
        rejectUnknown(map, ITEM_KEYS, file, "item " + id);
        ContentID parent = null;
        String parentValue = string(map, "extends", file, false);
        if (parentValue != null) parent = ContentID.parse(parentValue, pack.manifest().namespace());

        ItemType type = null;
        String typeValue = string(map, "type", file, false);
        if (typeValue != null) {
            try { type = ItemType.valueOf(typeValue.trim().toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException ex) { throw new ContentLoadException("Unknown item type '" + typeValue + "' for " + id + " in " + file); }
        }

        List<String> lore = null;
        if (map.containsKey("lore")) {
            Object value = map.get("lore");
            if (!(value instanceof List)) throw new ContentLoadException("lore must be a list for " + id + " in " + file);
            lore = new ArrayList<String>();
            for (Object line : (List<?>) value) {
                if (!(line instanceof String)) throw new ContentLoadException("lore entries must be strings for " + id + " in " + file);
                lore.add((String) line);
            }
        }

        Boolean bound = null;
        if (map.containsKey("bound")) {
            Object value = map.get("bound");
            if (!(value instanceof Boolean)) throw new ContentLoadException("bound must be boolean for " + id + " in " + file);
            bound = (Boolean) value;
        }

        RawItemRenderDefinition render = null;
        if (map.containsKey("render")) {
            Object value = map.get("render");
            if (!(value instanceof Map)) throw new ContentLoadException("render must be a mapping for " + id + " in " + file);
            Map<?, ?> renderMap = (Map<?, ?>) value;
            rejectUnknown(renderMap, RENDER_KEYS, file, "render for " + id);
            String model = string(renderMap, "model", file, false);
            Integer cmd = null;
            if (renderMap.containsKey("legacy_custom_model_data")) {
                Object raw = renderMap.get("legacy_custom_model_data");
                if (!(raw instanceof Number)) throw new ContentLoadException("legacy_custom_model_data must be numeric for " + id + " in " + file);
                cmd = ((Number) raw).intValue();
            }
            render = new RawItemRenderDefinition(model, cmd);
        }

        Map<String, Object> properties = null;
        if (map.containsKey("properties")) {
            Object value = map.get("properties");
            if (!(value instanceof Map)) throw new ContentLoadException("properties must be a mapping for " + id + " in " + file);
            properties = normalizeMap((Map<?, ?>) value, file);
        }

        return new RawItemDefinition(id, parent, type, string(map, "material", file, false),
                string(map, "display_name", file, false), lore, bound, render, properties);
    }

    private static String string(Map<?, ?> map, String key, Path file, boolean required) {
        if (!map.containsKey(key)) {
            if (required) throw new ContentLoadException("Missing required key '" + key + "' in " + file);
            return null;
        }
        Object value = map.get(key);
        if (!(value instanceof String)) throw new ContentLoadException(key + " must be a string in " + file);
        return (String) value;
    }

    private static void rejectUnknown(Map<?, ?> map, Set<String> allowed, Path file, String context) {
        for (Object key : map.keySet()) {
            if (!(key instanceof String) || !allowed.contains(key)) {
                throw new ContentLoadException("Unsupported key '" + key + "' in " + context + " at " + file);
            }
        }
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> source, Path file) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String)) throw new ContentLoadException("Property keys must be strings in " + file);
            result.put((String) entry.getKey(), normalizeValue(entry.getValue(), file));
        }
        return result;
    }

    private static Object normalizeValue(Object value, Path file) {
        if (value instanceof Map) return normalizeMap((Map<?, ?>) value, file);
        if (value instanceof List) {
            List<Object> result = new ArrayList<Object>();
            for (Object element : (List<?>) value) result.add(normalizeValue(element, file));
            return result;
        }
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) return value;
        throw new ContentLoadException("Unsupported property value type " + value.getClass().getName() + " in " + file);
    }
}
