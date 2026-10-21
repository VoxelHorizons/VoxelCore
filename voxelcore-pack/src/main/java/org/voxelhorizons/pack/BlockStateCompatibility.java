package org.voxelhorizons.pack;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Merges authored blockstate selectors into generated carrier blockstates. */
final class BlockStateCompatibility {
    private static final Gson GSON = new Gson();

    private BlockStateCompatibility() {}

    static boolean isBlockState(String path) {
        return path.startsWith("assets/") && path.contains("/blockstates/") && path.endsWith(".json");
    }

    /**
     * Generated carrier tables are complete and provide the normal vanilla model for every
     * state. An authored partial selector (for example {@code note=4}) acts as a fallback for
     * matching vanilla states. Explicit VoxelCore allocations retain priority, so generated
     * custom block models are never hidden by a broad authored selector.
     */
    static byte[] merge(byte[] generatedBytes, byte[] authoredBytes, String path) {
        Map<String, Object> generated = parse(generatedBytes, path);
        Map<String, Object> authored = parse(authoredBytes, path);
        Object generatedVariants = generated.get("variants");
        Object authoredVariants = authored.get("variants");

        if (generatedVariants instanceof Map && authoredVariants instanceof Map) {
            Map<String, Object> merged = new LinkedHashMap<String, Object>();
            Map<String, Object> generatedMap = stringMap(generatedVariants);
            Map<String, Object> authoredMap = stringMap(authoredVariants);
            merged.putAll(generatedMap);
            String fallbackModel = fallbackModel(path);

            // Apply authored selectors to generated vanilla fallbacks. More specific selectors
            // win, independent of source JSON key order. Allocated generated models take priority.
            for (Map.Entry<String, Object> generatedEntry : generatedMap.entrySet()) {
                if (!isFallback(generatedEntry.getValue(), fallbackModel)) continue;
                Map<String, String> state = properties(generatedEntry.getKey());
                Map.Entry<String, Object> best = null;
                int bestSpecificity = -1;
                for (Map.Entry<String, Object> authoredEntry : authoredMap.entrySet()) {
                    Map<String, String> selector = properties(authoredEntry.getKey());
                    if (selector.isEmpty() || selector.size() <= bestSpecificity || !matches(selector, state)) continue;
                    best = authoredEntry;
                    bestSpecificity = selector.size();
                }
                if (best != null) merged.put(generatedEntry.getKey(), best.getValue());
            }

            // Preserve authored selectors which do not match any generated full-state key.
            for (Map.Entry<String, Object> authoredEntry : authoredMap.entrySet()) {
                boolean represented = false;
                Map<String, String> selector = properties(authoredEntry.getKey());
                for (String key : generatedMap.keySet()) {
                    if (key.equals(authoredEntry.getKey()) || matches(selector, properties(key))) {
                        represented = true;
                        break;
                    }
                }
                if (!represented) merged.put(authoredEntry.getKey(), authoredEntry.getValue());
            }
            generated.put("variants", merged);
        } else if (generatedVariants == null && authoredVariants != null) {
            generated.put("variants", authoredVariants);
        }

        // Multipart rules are additive; preserve order and avoid duplicate predicates/models.
        Object generatedMultipart = generated.get("multipart");
        Object authoredMultipart = authored.get("multipart");
        if (generatedMultipart instanceof List && authoredMultipart instanceof List) {
            List<Object> merged = new ArrayList<Object>((List<?>) generatedMultipart);
            for (Object part : (List<?>) authoredMultipart) if (!merged.contains(part)) merged.add(part);
            generated.put("multipart", merged);
        } else if (generatedMultipart == null && authoredMultipart != null) {
            generated.put("multipart", authoredMultipart);
        }

        for (Map.Entry<String, Object> entry : authored.entrySet()) {
            if (!"variants".equals(entry.getKey()) && !"multipart".equals(entry.getKey()))
                generated.put(entry.getKey(), entry.getValue());
        }
        return (json(generated) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, Object> parse(byte[] bytes, String path) {
        try {
            Object value = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), Object.class);
            if (!(value instanceof Map)) throw new JavaPackCompileException("Invalid blockstate JSON object: " + path);
            return stringMap(value);
        } catch (JsonSyntaxException exception) {
            throw new JavaPackCompileException("Invalid JSON at " + path + ": " + exception.getMessage(), exception);
        }
    }

    private static Map<String, Object> stringMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
                if (entry.getKey() instanceof String) result.put((String) entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static String fallbackModel(String path) {
        int slash = path.lastIndexOf('/');
        String file = path.substring(slash + 1, path.length() - ".json".length());
        return "minecraft:block/" + file;
    }

    private static boolean isFallback(Object variant, String model) {
        if (!(variant instanceof Map)) return false;
        Object referenced = ((Map<?, ?>) variant).get("model");
        return model.equals(referenced) || model.substring("minecraft:".length()).equals(referenced);
    }

    private static Map<String, String> properties(String key) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (key == null || key.isEmpty()) return values;
        for (String part : key.split(",")) {
            int equals = part.indexOf('=');
            if (equals <= 0 || equals == part.length() - 1) return new LinkedHashMap<String, String>();
            values.put(part.substring(0, equals).trim(), part.substring(equals + 1).trim());
        }
        return values;
    }

    private static boolean matches(Map<String, String> selector, Map<String, String> state) {
        if (selector.isEmpty()) return false;
        for (Map.Entry<String, String> entry : selector.entrySet())
            if (!entry.getValue().equals(state.get(entry.getKey()))) return false;
        return true;
    }

    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map) {
            StringBuilder result = new StringBuilder("{");
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (result.length() > 1) result.append(',');
                result.append(json(entry.getKey().toString())).append(':').append(json(entry.getValue()));
            }
            return result.append('}').toString();
        }
        if (value instanceof List) {
            StringBuilder result = new StringBuilder("[");
            for (Object item : (List<?>) value) {
                if (result.length() > 1) result.append(',');
                result.append(json(item));
            }
            return result.append(']').toString();
        }
        String text = value.toString();
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') result.append('\\').append(c);
            else if (c == '\n') result.append("\\n");
            else if (c == '\r') result.append("\\r");
            else if (c == '\t') result.append("\\t");
            else if (c < 32) {
                String hex = Integer.toHexString(c);
                result.append("\\u");
                for (int pad = hex.length(); pad < 4; pad++) result.append('0');
                result.append(hex);
            } else result.append(c);
        }
        return result.append('"').toString();
    }
}
