package org.voxelhorizons.content.load;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.action.EventActions;
import org.voxelhorizons.content.block.BlockMethod;
import org.voxelhorizons.content.block.BlockModelPreset;
import org.voxelhorizons.content.block.RawBlockDefinition;
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

public final class BlockDefinitionParser {
    private static final Set<String> KEYS = new HashSet<String>(Arrays.asList(
            "extends", "abstract", "method", "model", "display_name", "texture", "textures", "hardness",
            "blast_resistance", "break_tools", "minimum_tool_tier", "explosion_immune",
            "stackable", "drop_when_mined", "drop", "silk_touch", "events"
    ));
    private final ActionDefinitionParser actions = new ActionDefinitionParser();

    public List<RawBlockDefinition> parse(ContentPack pack, Path file) {
        Object loaded;
        try (InputStream input = Files.newInputStream(file)) {
            loaded = ContentPackDiscovery.yaml().load(input);
        } catch (IOException exception) {
            throw new ContentLoadException("Unable to read block definitions: " + file, exception);
        } catch (RuntimeException exception) {
            throw new ContentLoadException("Invalid YAML in block definitions: " + file, exception);
        }
        if (!(loaded instanceof Map)) throw new ContentLoadException("Definition file must be a mapping: " + file);
        Object rawBlocks = ((Map<?, ?>) loaded).get("blocks");
        if (rawBlocks == null) return Collections.emptyList();
        if (!(rawBlocks instanceof Map)) throw new ContentLoadException("'blocks' must be a mapping in " + file);

        List<RawBlockDefinition> definitions = new ArrayList<RawBlockDefinition>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawBlocks).entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Map)) {
                throw new ContentLoadException("Each block must be a named mapping in " + file);
            }
            ContentID id = ContentID.parse(((String) entry.getKey()).trim(), pack.manifest().namespace());
            if (!id.namespace().equals(pack.manifest().namespace())) {
                throw new ContentLoadException("Block " + id + " in " + file + " must use pack namespace "
                        + pack.manifest().namespace());
            }
            definitions.add(parseBlock(pack, file, id, (Map<?, ?>) entry.getValue()));
        }
        return definitions;
    }

    private RawBlockDefinition parseBlock(ContentPack pack, Path file, ContentID id, Map<?, ?> map) {
        rejectUnknown(map, file, id);
        ContentID parent = contentId(map, "extends", pack, file, id);
        Boolean abstractDefinition = bool(map, "abstract", file, id);
        BlockMethod method = null;
        if (map.containsKey("method")) {
            try { method = BlockMethod.parse(string(map, "method", file, id)); }
            catch (IllegalArgumentException exception) { throw new ContentLoadException(exception.getMessage() + " for " + id + " in " + file); }
        }
        BlockModelPreset model = null;
        if (map.containsKey("model")) {
            try { model = BlockModelPreset.parse(string(map, "model", file, id)); }
            catch (IllegalArgumentException exception) { throw new ContentLoadException(exception.getMessage() + " for " + id + " in " + file); }
        }
        String displayName = map.containsKey("display_name") ? string(map, "display_name", file, id) : null;
        String texture = map.containsKey("texture") ? texture(string(map, "texture", file, id), file, id) : null;
        Map<String, String> textures = null;
        if (map.containsKey("textures")) {
            Object value = map.get("textures");
            if (!(value instanceof Map)) throw new ContentLoadException("textures must be a mapping for " + id + " in " + file);
            textures = new LinkedHashMap<String, String>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
                    throw new ContentLoadException("texture names and values must be strings for " + id + " in " + file);
                }
                String face = ((String) entry.getKey()).trim().toLowerCase(java.util.Locale.ROOT);
                if (!Arrays.asList("all", "side", "top", "bottom", "north", "south", "east", "west", "up", "down").contains(face)) {
                    throw new ContentLoadException("unsupported texture face '" + face + "' for " + id + " in " + file);
                }
                textures.put(face, texture((String) entry.getValue(), file, id));
            }
        }
        if (texture != null && textures != null) {
            throw new ContentLoadException("Use either texture or textures, not both, for " + id + " in " + file);
        }

        Double hardness = number(map, "hardness", file, id, 0.0D);
        Double resistance = number(map, "blast_resistance", file, id, 0.0D);
        List<String> breakTools = stringList(map, "break_tools", file, id);
        String minimumToolTier = map.containsKey("minimum_tool_tier")
                ? string(map, "minimum_tool_tier", file, id).toUpperCase(java.util.Locale.ROOT) : null;
        if (minimumToolTier != null && !Arrays.asList("WOOD", "GOLD", "STONE", "IRON", "DIAMOND", "NETHERITE").contains(minimumToolTier)) {
            throw new ContentLoadException("minimum_tool_tier must be WOOD, GOLD, STONE, IRON, DIAMOND, or NETHERITE for " + id + " in " + file);
        }
        Boolean stackable = bool(map, "stackable", file, id);
        Boolean explosionImmune = bool(map, "explosion_immune", file, id);
        Boolean dropWhenMined = bool(map, "drop_when_mined", file, id);
        ContentID drop = contentId(map, "drop", pack, file, id);
        ContentID silk = contentId(map, "silk_touch", pack, file, id);
        EventActions events = map.containsKey("events") ? actions.parse(map.get("events"), id, file) : null;
        return new RawBlockDefinition(id, parent, abstractDefinition, method, model, displayName, texture, textures,
                hardness, resistance, breakTools, minimumToolTier, stackable, explosionImmune,
                dropWhenMined, drop, silk, events);
    }

    private static void rejectUnknown(Map<?, ?> map, Path file, ContentID id) {
        for (Object key : map.keySet()) {
            if (!(key instanceof String) || !KEYS.contains(key)) {
                throw new ContentLoadException("Unsupported key '" + key + "' in block " + id + " at " + file);
            }
        }
    }

    private static String string(Map<?, ?> map, String key, Path file, ContentID id) {
        Object value = map.get(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new ContentLoadException(key + " must be a non-empty string for " + id + " in " + file);
        }
        return ((String) value).trim();
    }

    private static String texture(String value, Path file, ContentID id) {
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[a-z0-9._-]+:[a-z0-9/._-]+")) {
            throw new ContentLoadException("texture must be namespace:path for " + id + " in " + file);
        }
        return normalized;
    }

    private static ContentID contentId(Map<?, ?> map, String key, ContentPack pack, Path file, ContentID owner) {
        if (!map.containsKey(key)) return null;
        try { return ContentID.parse(string(map, key, file, owner), pack.manifest().namespace()); }
        catch (IllegalArgumentException exception) {
            throw new ContentLoadException("Invalid " + key + " for " + owner + " in " + file + ": " + exception.getMessage());
        }
    }

    private static Boolean bool(Map<?, ?> map, String key, Path file, ContentID id) {
        if (!map.containsKey(key)) return null;
        Object value = map.get(key);
        if (!(value instanceof Boolean)) throw new ContentLoadException(key + " must be boolean for " + id + " in " + file);
        return (Boolean) value;
    }

    private static List<String> stringList(Map<?, ?> map, String key, Path file, ContentID id) {
        if (!map.containsKey(key)) return null;
        Object value = map.get(key);
        if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
            throw new ContentLoadException(key + " must be a non-empty list for " + id + " in " + file);
        }
        List<String> values = new ArrayList<String>();
        for (Object entry : (List<?>) value) {
            if (!(entry instanceof String) || ((String) entry).trim().isEmpty()) {
                throw new ContentLoadException(key + " entries must be non-empty strings for " + id + " in " + file);
            }
            values.add(((String) entry).trim().toUpperCase(java.util.Locale.ROOT));
        }
        return values;
    }

    private static Double number(Map<?, ?> map, String key, Path file, ContentID id, double minimum) {
        if (!map.containsKey(key)) return null;
        Object value = map.get(key);
        if (!(value instanceof Number) || ((Number) value).doubleValue() < minimum) {
            throw new ContentLoadException(key + " must be a number >= " + minimum + " for " + id + " in " + file);
        }
        return Double.valueOf(((Number) value).doubleValue());
    }
}
