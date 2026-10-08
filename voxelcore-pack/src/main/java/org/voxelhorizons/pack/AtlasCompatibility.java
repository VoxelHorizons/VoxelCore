package org.voxelhorizons.pack;

import org.voxelhorizons.content.pack.ContentPackDiscovery;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Reconciles vanilla and authored atlas sources and fixes mixed block/item cuboid models
 * in generated ZIP entries without rewriting the original resource pack source files.
 */
final class AtlasCompatibility {
    private static final String ITEMS = "assets/minecraft/atlases/items.json";
    private static final String BLOCKS = "assets/minecraft/atlases/blocks.json";

    private AtlasCompatibility() {}

    static boolean isAtlas(String path) {
        return path.startsWith("assets/") && path.contains("/atlases/") && path.endsWith(".json");
    }

    static byte[] merge(byte[] original, byte[] additional, String path) {
        Map<String, Object> a = parse(original, path), b = parse(additional, path);
        Object sourcesA = a.get("sources"), sourcesB = b.get("sources");
        if (!(sourcesA instanceof List) || !(sourcesB instanceof List))
            throw new JavaPackCompileException("Atlas is missing sources: " + path);
        List<Object> sources = new ArrayList<Object>();
        Set<String> seen = new HashSet<String>();
        for (Object source : (List<?>) sourcesA) if (seen.add(json(source))) sources.add(source);
        for (Object source : (List<?>) sourcesB) if (seen.add(json(source))) sources.add(source);
        a.putAll(b);
        a.put("sources", sources);
        return utf8(json(a));
    }

    static void repair(Map<String, byte[]> entries) {
        if (entries.containsKey(ITEMS))
            entries.put(ITEMS, merge(vanillaItems(), entries.get(ITEMS), ITEMS));

        Map<String, Model> models = new TreeMap<String, Model>();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String file = entry.getKey();
            int modelsAt = file.indexOf("/models/");
            if (modelsAt < 0 || !file.startsWith("assets/") || !file.endsWith(".json")) continue;
            int endNamespace = file.indexOf('/', 7);
            if (endNamespace < 0 || endNamespace >= modelsAt) continue;
            String name = file.substring(7, endNamespace) + ":" + file.substring(modelsAt + 8, file.length() - 5);
            models.put(name, new Model(file, parse(entry.getValue(), file)));
        }

        Map<String, String> blockAliases = new TreeMap<String, String>();
        Map<String, String> itemAliases = new TreeMap<String, String>();
        for (String id : models.keySet())
            resolve(id, models, entries, new HashSet<String>(), blockAliases, itemAliases);

        if (!blockAliases.isEmpty()) {
            byte[] current = entries.get(BLOCKS);
            byte[] base = current == null ? vanillaBlocks() : merge(vanillaBlocks(), current, BLOCKS);
            entries.put(BLOCKS, merge(base, aliases(blockAliases), BLOCKS));
        }
        if (!itemAliases.isEmpty()) {
            byte[] base = entries.containsKey(ITEMS) ? entries.get(ITEMS) : vanillaItems();
            entries.put(ITEMS, merge(base, aliases(itemAliases), ITEMS));
        }
    }

    private static Map<String, String> resolve(String id, Map<String, Model> models,
             Map<String, byte[]> entries, Set<String> stack,
             Map<String, String> blockAliases, Map<String, String> itemAliases) {
        Model model = models.get(id);
        if (model == null) return Collections.emptyMap();
        if (model.resolved != null) return model.resolved;
        if (!stack.add(id)) throw new JavaPackCompileException("Model inheritance cycle: " + id);

        Map<String, String> textures = new LinkedHashMap<String, String>();
        Object parent = model.root.get("parent");
        if (parent instanceof String)
            textures.putAll(resolve(qualify((String) parent), models, entries, stack, blockAliases, itemAliases));
        Map<String, Object> own = map(model.root.get("textures"));
        for (Map.Entry<String, Object> row : own.entrySet())
            if (row.getValue() instanceof String) textures.put(row.getKey(), (String) row.getValue());

        boolean blocks = false, items = false;
        for (String value : textures.values()) {
            int atlas = atlas(follow(value, textures));
            blocks |= atlas == 1;
            items |= atlas == 2;
        }
        if (blocks && items) {
            boolean preferItem = id.contains(":item/") || own.containsKey("layer0");
            Map<String, String> aliasMap = preferItem ? itemAliases : blockAliases;
            int foreign = preferItem ? 1 : 2;
            for (Map.Entry<String, String> row : new ArrayList<Map.Entry<String, String>>(textures.entrySet())) {
                String sprite = follow(row.getValue(), textures);
                if (atlas(sprite) != foreign) continue;
                String resource = qualify(sprite);
                String alias = "voxelcore:atlas_compat/" + (preferItem ? "items/" : "blocks/") +
                        resource.replace(':', '/');
                aliasMap.put(alias, resource);
                own.put(row.getKey(), alias);
                textures.put(row.getKey(), alias);
            }
            model.root.put("textures", own);
            entries.put(model.file, utf8(json(model.root)));
        }
        model.resolved = textures;
        stack.remove(id);
        return textures;
    }

    private static String follow(String value, Map<String, String> refs) {
        Set<String> visited = new HashSet<String>();
        while (value != null && value.startsWith("#")) {
            String ref = value.substring(1);
            if (!visited.add(ref)) return null;
            value = refs.get(ref);
        }
        return value;
    }

    private static int atlas(String sprite) {
        if (sprite == null || sprite.startsWith("#")) return 0;
        String id = qualify(sprite), p = id.substring(id.indexOf(':') + 1);
        if (p.startsWith("block/") || p.startsWith("atlas_compat/blocks/")) return 1;
        if (p.startsWith("item/") || p.startsWith("atlas_compat/items/")) return 2;
        return !id.startsWith("minecraft:") && !p.startsWith("entity/") ? 2 : 0;
    }

    private static String qualify(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    private static byte[] aliases(Map<String, String> aliases) {
        List<Object> sources = new ArrayList<Object>();
        for (Map.Entry<String, String> a : aliases.entrySet()) {
            Map<String, Object> source = new LinkedHashMap<String, Object>();
            source.put("type", "minecraft:single");
            source.put("resource", a.getValue());
            source.put("sprite", a.getKey());
            sources.add(source);
        }
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("sources", sources);
        return utf8(json(root));
    }

    private static byte[] vanillaBlocks() {
        return utf8("{\"sources\":[{\"type\":\"minecraft:directory\",\"source\":\"block\",\"prefix\":\"block/\"}]}");
    }

    private static byte[] vanillaItems() {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        List<Object> sources = new ArrayList<Object>();
        Map<String, Object> directory = new LinkedHashMap<String, Object>();
        directory.put("type", "minecraft:directory");
        directory.put("source", "item");
        directory.put("prefix", "item/");
        sources.add(directory);
        Map<String, Object> trims = new LinkedHashMap<String, Object>();
        trims.put("type", "minecraft:paletted_permutations");
        trims.put("palette_key", "minecraft:trims/color_palettes/trim_palette");
        Map<String, String> colors = new LinkedHashMap<String, String>();
        for (String color : Arrays.asList("amethyst", "copper", "copper_darker", "diamond",
                "diamond_darker", "emerald", "gold", "gold_darker", "iron", "iron_darker",
                "lapis", "netherite", "netherite_darker", "quartz", "redstone", "resin"))
            colors.put(color, "minecraft:trims/color_palettes/" + color);
        trims.put("permutations", colors);
        trims.put("textures", Arrays.asList("minecraft:trims/items/helmet_trim",
                "minecraft:trims/items/chestplate_trim", "minecraft:trims/items/leggings_trim",
                "minecraft:trims/items/boots_trim"));
        sources.add(trims);
        root.put("sources", sources);
        return utf8(json(root));
    }

    private static Map<String, Object> map(Object obj) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (obj instanceof Map) for (Map.Entry<?, ?> e : ((Map<?, ?>) obj).entrySet())
            if (e.getKey() instanceof String) result.put((String) e.getKey(), e.getValue());
        return result;
    }

    private static Map<String, Object> parse(byte[] text, String path) {
        try {
            Object parsed = ContentPackDiscovery.yaml().load(new ByteArrayInputStream(text));
            if (!(parsed instanceof Map))
                throw new JavaPackCompileException("Invalid JSON object: " + path);
            return map(parsed);
        } catch (RuntimeException err) {
            throw new JavaPackCompileException("Invalid JSON at " + path + ": " + err.getMessage(), err);
        }
    }

    private static byte[] utf8(String text) {
        return (text + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static String json(Object object) {
        if (object == null) return "null";
        if (object instanceof Number || object instanceof Boolean) return object.toString();
        if (object instanceof Map) {
            StringBuilder out = new StringBuilder("{");
            for (Map.Entry<?, ?> e : ((Map<?, ?>) object).entrySet()) {
                if (out.length() > 1) out.append(',');
                out.append(json(e.getKey().toString())).append(':').append(json(e.getValue()));
            }
            return out.append("}").toString();
        }
        if (object instanceof List) {
            StringBuilder out = new StringBuilder("[");
            for (Object value : (List<?>) object) {
                if (out.length() > 1) out.append(',');
                out.append(json(value));
            }
            return out.append("]").toString();
        }
        StringBuilder out = new StringBuilder("\"");
        String value = object.toString();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '"' || ch == '\\') out.append('\\').append(ch);
            else if (ch == '\n') out.append("\\n");
            else if (ch == '\r') out.append("\\r");
            else if (ch == '\t') out.append("\\t");
            else if (ch < 32) {
                String h = Integer.toHexString(ch);
                out.append("\\u");
                for (int j = h.length(); j < 4; j++) out.append('0');
                out.append(h);
            } else out.append(ch);
        }
        return out.append('"').toString();
    }

    private static final class Model {
        final String file;
        final Map<String, Object> root;
        Map<String, String> resolved;
        Model(String file, Map<String, Object> root) { this.file = file; this.root = root; }
    }
}
