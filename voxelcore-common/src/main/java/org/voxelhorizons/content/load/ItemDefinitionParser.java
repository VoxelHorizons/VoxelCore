package org.voxelhorizons.content.load;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.CustomModelDataDefinition;
import org.voxelhorizons.content.item.ItemType;
import org.voxelhorizons.content.item.RawItemDefinition;
import org.voxelhorizons.content.item.RawItemRenderDefinition;
import org.voxelhorizons.content.pack.ContentPack;
import org.voxelhorizons.content.pack.ContentPackDiscovery;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
            "model", "unbreakable", "durability", "attributes", "custom_model_data"
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
        if (!(loaded instanceof Map)) throw new ContentLoadException("Definition file must be a mapping: " + file);
        Map<?, ?> root = (Map<?, ?>) loaded;
        for (Object key : root.keySet()) {
            if (!"items".equals(key)) throw new ContentLoadException("Unsupported top-level key '" + key + "' in " + file);
        }
        Object itemsValue = root.get("items");
        if (itemsValue == null) return Collections.emptyList();
        if (!(itemsValue instanceof Map)) throw new ContentLoadException("'items' must be a mapping in " + file);

        List<RawItemDefinition> definitions = new ArrayList<RawItemDefinition>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) itemsValue).entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Map)) {
                throw new ContentLoadException("Each item must be a named mapping in " + file);
            }
            ContentID id = ContentID.parse(((String) entry.getKey()).trim(), pack.manifest().namespace());
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
            Boolean unbreakable = booleanValue(renderMap, "unbreakable", id, file);
            Integer durability = nonNegativeInteger(renderMap, "durability", id, file);
            Map<String, Boolean> attributes = booleanMap(renderMap, "attributes", id, file);
            CustomModelDataDefinition customModelData = parseCustomModelData(renderMap, id, file);
            render = new RawItemRenderDefinition(model, unbreakable, durability, attributes, customModelData);
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

    private static Boolean booleanValue(Map<?, ?> map, String key, ContentID id, Path file) {
        if (!map.containsKey(key)) return null;
        Object value = map.get(key);
        if (!(value instanceof Boolean)) throw new ContentLoadException(key + " must be boolean for " + id + " in " + file);
        return (Boolean) value;
    }

    private static Integer nonNegativeInteger(Map<?, ?> map, String key, ContentID id, Path file) {
        if (!map.containsKey(key)) return null;
        Object raw = map.get(key);
        if (!(raw instanceof Number)) throw new ContentLoadException(key + " must be an integer for " + id + " in " + file);
        Number number = (Number) raw;
        double value = number.doubleValue();
        int integer = number.intValue();
        if (value != integer || integer < 0) {
            throw new ContentLoadException(key + " must be a non-negative whole number for " + id + " in " + file);
        }
        return Integer.valueOf(integer);
    }

    private static Map<String, Boolean> booleanMap(Map<?, ?> map, String key, ContentID id, Path file) {
        if (!map.containsKey(key)) return null;
        Object raw = map.get(key);
        if (!(raw instanceof Map)) throw new ContentLoadException(key + " must be a mapping for " + id + " in " + file);
        Map<String, Boolean> values = new LinkedHashMap<String, Boolean>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            if (!(entry.getKey() instanceof String) || ((String) entry.getKey()).trim().isEmpty()) {
                throw new ContentLoadException(key + " keys must be non-empty strings for " + id + " in " + file);
            }
            if (!(entry.getValue() instanceof Boolean)) {
                throw new ContentLoadException(key + " value for '" + entry.getKey() + "' must be boolean for " + id + " in " + file);
            }
            values.put(((String) entry.getKey()).trim().toLowerCase(java.util.Locale.ROOT), (Boolean) entry.getValue());
        }
        return values;
    }

    private static CustomModelDataDefinition parseCustomModelData(Map<?, ?> renderMap, ContentID id, Path file) {
        if (!renderMap.containsKey("custom_model_data")) return null;
        Object raw = renderMap.get("custom_model_data");
        if (raw instanceof Number) {
            Number number = (Number) raw;
            double value = number.doubleValue();
            int integer = number.intValue();
            if (value != integer || integer < 0) {
                throw new ContentLoadException("custom_model_data integer must be a non-negative whole number for " + id + " in " + file);
            }
            return CustomModelDataDefinition.numeric(integer);
        }
        if (!(raw instanceof Map)) {
            throw new ContentLoadException("custom_model_data must be an integer or mapping for " + id + " in " + file);
        }
        Map<String, CustomModelDataDefinition.Value> values = new LinkedHashMap<String, CustomModelDataDefinition.Value>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            if (!(entry.getKey() instanceof String) || ((String) entry.getKey()).trim().isEmpty()) {
                throw new ContentLoadException("custom_model_data keys must be non-empty strings for " + id + " in " + file);
            }
            String key = ((String) entry.getKey()).trim();
            if (values.containsKey(key)) throw new ContentLoadException("Duplicate custom_model_data key '" + key + "' for " + id + " in " + file);
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                values.put(key, CustomModelDataDefinition.Value.flag(((Boolean) value).booleanValue()));
            } else if (value instanceof Number) {
                values.put(key, CustomModelDataDefinition.Value.floating(((Number) value).floatValue()));
            } else if (value instanceof String) {
                String text = (String) value;
                if (text.matches("#[0-9a-fA-F]{6}")) {
                    values.put(key, CustomModelDataDefinition.Value.color(Integer.parseInt(text.substring(1), 16)));
                } else {
                    values.put(key, CustomModelDataDefinition.Value.string(text));
                }
            } else {
                throw new ContentLoadException("custom_model_data value for '" + key + "' must be number, boolean or string for " + id + " in " + file);
            }
        }
        try {
            return CustomModelDataDefinition.structured(values);
        } catch (IllegalArgumentException exception) {
            throw new ContentLoadException("Invalid custom_model_data for " + id + " in " + file + ": " + exception.getMessage(), exception);
        }
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
