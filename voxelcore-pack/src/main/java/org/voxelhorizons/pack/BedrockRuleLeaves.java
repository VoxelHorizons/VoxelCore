package org.voxelhorizons.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Flattens a VoxelCore {@code render.rule} tree (the same grammar JavaPackCompilerEngine
 * turns into a 1.21.4+ items/*.json select/condition/range_dispatch tree) into Geyser v2
 * mapping leaves. Each leaf carries the conjunction of custom_model_data predicates on its
 * path and a priority reproducing Java's evaluation order: Geyser checks definitions that
 * share a Java item model by descending {@code priority} (ItemRegistryPopulator), and its
 * range_dispatch predicate matches {@code value >= threshold}.
 */
final class BedrockRuleLeaves {
    enum ValueType { STRING, FLAG, FLOAT }

    /** Resolves a rule key to its stable custom_model_data list index. */
    interface IndexResolver {
        Integer index(ValueType type, String key);
    }

    static final class Leaf {
        final String model;
        final JsonArray predicates;
        final int priority;
        final boolean tinted;
        Leaf(String model, JsonArray predicates, int priority, boolean tinted) {
            this.model = model; this.predicates = predicates; this.priority = priority; this.tinted = tinted;
        }
    }

    private BedrockRuleLeaves() {}

    /** Leaves in Java evaluation order; priorities strictly decrease. */
    static List<Leaf> flatten(Object rule, String defaultNamespace, IndexResolver indices) throws IOException {
        List<Object[]> raw = new ArrayList<Object[]>();
        walk(rule, defaultNamespace, indices, new JsonArray(), raw);
        List<Leaf> leaves = new ArrayList<Leaf>();
        for (int i = 0; i < raw.size(); i++) {
            Object[] entry = raw.get(i);
            leaves.add(new Leaf((String) entry[0], (JsonArray) entry[1], raw.size() - i, (Boolean) entry[2]));
        }
        return Collections.unmodifiableList(leaves);
    }

    private static void walk(Object node, String namespace, IndexResolver indices, JsonArray path,
                             List<Object[]> out) throws IOException {
        if (node instanceof String) {
            out.add(new Object[]{namespaced((String) node, namespace), path.deepCopy(), Boolean.FALSE});
            return;
        }
        if (!(node instanceof Map)) throw new IOException("Render rule node must be a model string or mapping");
        Map<?, ?> map = (Map<?, ?>) node;
        if (map.size() != 1) throw new IOException("Render rule node must contain exactly one of model, select, condition or range");
        if (map.containsKey("model")) {
            Object model = map.get("model");
            if (model instanceof String) {
                out.add(new Object[]{namespaced((String) model, namespace), path.deepCopy(), Boolean.FALSE});
            } else if (model instanceof Map && ((Map<?, ?>) model).get("id") instanceof String) {
                // Bedrock items have no runtime tint source: map the model, flag the tint.
                out.add(new Object[]{namespaced((String) ((Map<?, ?>) model).get("id"), namespace), path.deepCopy(),
                        Boolean.valueOf(((Map<?, ?>) model).containsKey("tint"))});
            } else {
                throw new IOException("render.rule.model must be a string or {id, tint}");
            }
        } else if (map.containsKey("select")) {
            Map<?, ?> select = map(map.get("select"), "select");
            int index = index(indices, ValueType.STRING, select.get("key"));
            Map<?, ?> cases = map(select.get("cases"), "select.cases");
            for (Object value : new TreeMap<Object, Object>(stringKeys(cases)).keySet()) {
                JsonObject predicate = predicate("match", index);
                predicate.addProperty("value", String.valueOf(value));
                walk(cases.get(value), namespace, indices, with(path, predicate), out);
            }
            if (select.containsKey("fallback")) walk(select.get("fallback"), namespace, indices, path, out);
        } else if (map.containsKey("condition")) {
            Map<?, ?> condition = map(map.get("condition"), "condition");
            int index = index(indices, ValueType.FLAG, condition.get("key"));
            for (boolean expected : new boolean[]{true, false}) {
                Object branch = condition.get(String.valueOf(expected));
                if (branch == null) throw new IOException("render.rule.condition requires true and false branches");
                JsonObject predicate = predicate("condition", index);
                predicate.addProperty("expected", expected);
                walk(branch, namespace, indices, with(path, predicate), out);
            }
        } else if (map.containsKey("range")) {
            Map<?, ?> range = map(map.get("range"), "range");
            int index = index(indices, ValueType.FLOAT, range.get("key"));
            Map<?, ?> entries = map(range.get("entries"), "range.entries");
            TreeMap<Double, Object> sorted = new TreeMap<Double, Object>(Collections.reverseOrder());
            for (Map.Entry<?, ?> entry : entries.entrySet()) {
                double threshold;
                try { threshold = Double.parseDouble(String.valueOf(entry.getKey())); }
                catch (NumberFormatException invalid) { throw new IOException("Range threshold is not numeric: " + entry.getKey()); }
                if (Double.isNaN(threshold) || Double.isInfinite(threshold)) throw new IOException("Range threshold must be finite");
                sorted.put(Double.valueOf(threshold), entry.getValue());
            }
            // Highest threshold first: value >= t picks the largest t not above value, as Java does.
            for (Map.Entry<Double, Object> entry : sorted.entrySet()) {
                JsonObject predicate = predicate("range_dispatch", index);
                predicate.add("threshold", new com.google.gson.JsonPrimitive(
                        BigDecimal.valueOf(entry.getKey().doubleValue()).stripTrailingZeros()));
                walk(entry.getValue(), namespace, indices, with(path, predicate), out);
            }
            if (range.containsKey("fallback")) walk(range.get("fallback"), namespace, indices, path, out);
        } else {
            throw new IOException("Render rule node must contain exactly one of model, select, condition or range");
        }
    }

    private static JsonObject predicate(String type, int index) {
        JsonObject predicate = new JsonObject();
        predicate.addProperty("type", type);
        predicate.addProperty("property", "custom_model_data");
        predicate.addProperty("index", index);
        return predicate;
    }

    private static JsonArray with(JsonArray path, JsonObject predicate) {
        JsonArray copy = path.deepCopy();
        copy.add(predicate);
        return copy;
    }

    private static int index(IndexResolver indices, ValueType type, Object key) throws IOException {
        if (!(key instanceof String)) throw new IOException("Render rule key must be a string");
        Integer index = indices.index(type, (String) key);
        if (index == null)
            throw new IOException("Render rule " + type.name().toLowerCase(java.util.Locale.ROOT)
                    + " key '" + key + "' has no stable custom_model_data allocation");
        return index.intValue();
    }

    private static Map<?, ?> map(Object raw, String context) throws IOException {
        if (!(raw instanceof Map)) throw new IOException("render.rule." + context + " must be a mapping");
        if (((Map<?, ?>) raw).isEmpty()) throw new IOException("render.rule." + context + " cannot be empty");
        return (Map<?, ?>) raw;
    }

    private static Map<String, Object> stringKeys(Map<?, ?> cases) throws IOException {
        Map<String, Object> out = new TreeMap<String, Object>();
        for (Map.Entry<?, ?> entry : cases.entrySet()) {
            if (!(entry.getKey() instanceof String)) throw new IOException("render.rule.select.cases keys must be strings");
            out.put((String) entry.getKey(), entry.getValue());
        }
        return out;
    }

    private static String namespaced(String model, String namespace) {
        return model.indexOf(':') >= 0 ? model : namespace + ":" + model;
    }
}
