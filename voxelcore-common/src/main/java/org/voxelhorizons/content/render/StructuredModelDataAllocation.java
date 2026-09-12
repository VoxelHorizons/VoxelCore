package org.voxelhorizons.content.render;

import org.voxelhorizons.content.item.CustomModelDataDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stable semantic-key to typed-list-index mapping for one modern item model. */
public final class StructuredModelDataAllocation {
    private final Map<String, Integer> floats;
    private final Map<String, Integer> flags;
    private final Map<String, Integer> strings;
    private final Map<String, Integer> colors;

    public StructuredModelDataAllocation(Map<String, Integer> floats,
                                         Map<String, Integer> flags,
                                         Map<String, Integer> strings,
                                         Map<String, Integer> colors) {
        this.floats = immutable(floats);
        this.flags = immutable(flags);
        this.strings = immutable(strings);
        this.colors = immutable(colors);
        validateUniqueIndexes(this.floats, CustomModelDataDefinition.ValueType.FLOAT);
        validateUniqueIndexes(this.flags, CustomModelDataDefinition.ValueType.FLAG);
        validateUniqueIndexes(this.strings, CustomModelDataDefinition.ValueType.STRING);
        validateUniqueIndexes(this.colors, CustomModelDataDefinition.ValueType.COLOR);
        validateSingleTypePerKey();
    }

    public static StructuredModelDataAllocation empty() {
        return new StructuredModelDataAllocation(Collections.<String, Integer>emptyMap(),
                Collections.<String, Integer>emptyMap(), Collections.<String, Integer>emptyMap(),
                Collections.<String, Integer>emptyMap());
    }

    public Map<String, Integer> floats() { return floats; }
    public Map<String, Integer> flags() { return flags; }
    public Map<String, Integer> strings() { return strings; }
    public Map<String, Integer> colors() { return colors; }

    public Map<String, Integer> indices(CustomModelDataDefinition.ValueType type) {
        switch (type) {
            case FLOAT: return floats;
            case FLAG: return flags;
            case STRING: return strings;
            case COLOR: return colors;
            default: throw new IllegalArgumentException("Unsupported custom model data value type " + type);
        }
    }

    public Optional<Integer> index(CustomModelDataDefinition.ValueType type, String key) {
        return Optional.ofNullable(indices(type).get(key));
    }

    public int size(CustomModelDataDefinition.ValueType type) {
        int maximum = -1;
        for (Integer value : indices(type).values()) maximum = Math.max(maximum, value.intValue());
        return maximum + 1;
    }

    public StructuredModelDataAllocation reconcile(CustomModelDataDefinition data) {
        if (data == null || !data.isStructured()) return this;

        Map<String, Integer> nextFloats = new LinkedHashMap<String, Integer>(floats);
        Map<String, Integer> nextFlags = new LinkedHashMap<String, Integer>(flags);
        Map<String, Integer> nextStrings = new LinkedHashMap<String, Integer>(strings);
        Map<String, Integer> nextColors = new LinkedHashMap<String, Integer>(colors);

        List<Map.Entry<String, CustomModelDataDefinition.Value>> values =
                new ArrayList<Map.Entry<String, CustomModelDataDefinition.Value>>(data.structuredValues().entrySet());
        Collections.sort(values, new Comparator<Map.Entry<String, CustomModelDataDefinition.Value>>() {
            @Override public int compare(Map.Entry<String, CustomModelDataDefinition.Value> left,
                                         Map.Entry<String, CustomModelDataDefinition.Value> right) {
                int type = left.getValue().type().name().compareTo(right.getValue().type().name());
                return type != 0 ? type : left.getKey().compareTo(right.getKey());
            }
        });

        for (Map.Entry<String, CustomModelDataDefinition.Value> entry : values) {
            String key = entry.getKey();
            CustomModelDataDefinition.ValueType type = entry.getValue().type();
            CustomModelDataDefinition.ValueType previousType = typeOf(key);
            if (previousType != null && previousType != type) {
                throw new IllegalArgumentException("Structured custom_model_data key '" + key
                        + "' changed type from " + previousType + " to " + type);
            }
            Map<String, Integer> target = mapFor(type, nextFloats, nextFlags, nextStrings, nextColors);
            if (!target.containsKey(key)) target.put(key, Integer.valueOf(nextIndex(target)));
        }
        return new StructuredModelDataAllocation(nextFloats, nextFlags, nextStrings, nextColors);
    }

    private CustomModelDataDefinition.ValueType typeOf(String key) {
        if (floats.containsKey(key)) return CustomModelDataDefinition.ValueType.FLOAT;
        if (flags.containsKey(key)) return CustomModelDataDefinition.ValueType.FLAG;
        if (strings.containsKey(key)) return CustomModelDataDefinition.ValueType.STRING;
        if (colors.containsKey(key)) return CustomModelDataDefinition.ValueType.COLOR;
        return null;
    }

    private static Map<String, Integer> mapFor(CustomModelDataDefinition.ValueType type,
                                                Map<String, Integer> floats,
                                                Map<String, Integer> flags,
                                                Map<String, Integer> strings,
                                                Map<String, Integer> colors) {
        switch (type) {
            case FLOAT: return floats;
            case FLAG: return flags;
            case STRING: return strings;
            case COLOR: return colors;
            default: throw new IllegalArgumentException("Unsupported custom model data value type " + type);
        }
    }

    private static int nextIndex(Map<String, Integer> values) {
        int maximum = -1;
        for (Integer value : values.values()) maximum = Math.max(maximum, value.intValue());
        return maximum + 1;
    }

    private static Map<String, Integer> immutable(Map<String, Integer> source) {
        Map<String, Integer> copy = new LinkedHashMap<String, Integer>();
        if (source != null) copy.putAll(source);
        return Collections.unmodifiableMap(copy);
    }

    private static void validateUniqueIndexes(Map<String, Integer> values, CustomModelDataDefinition.ValueType type) {
        Map<Integer, String> used = new LinkedHashMap<Integer, String>();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().trim().isEmpty()) {
                throw new IllegalArgumentException("Structured " + type + " key cannot be empty");
            }
            if (entry.getValue() == null || entry.getValue().intValue() < 0) {
                throw new IllegalArgumentException("Structured " + type + " index cannot be negative for " + entry.getKey());
            }
            String previous = used.put(entry.getValue(), entry.getKey());
            if (previous != null) {
                throw new IllegalArgumentException("Structured " + type + " index " + entry.getValue()
                        + " is assigned to both '" + previous + "' and '" + entry.getKey() + "'");
            }
        }
    }

    private void validateSingleTypePerKey() {
        Map<String, CustomModelDataDefinition.ValueType> types = new LinkedHashMap<String, CustomModelDataDefinition.ValueType>();
        recordTypes(types, floats, CustomModelDataDefinition.ValueType.FLOAT);
        recordTypes(types, flags, CustomModelDataDefinition.ValueType.FLAG);
        recordTypes(types, strings, CustomModelDataDefinition.ValueType.STRING);
        recordTypes(types, colors, CustomModelDataDefinition.ValueType.COLOR);
    }

    private static void recordTypes(Map<String, CustomModelDataDefinition.ValueType> types,
                                    Map<String, Integer> values,
                                    CustomModelDataDefinition.ValueType type) {
        for (String key : values.keySet()) {
            CustomModelDataDefinition.ValueType previous = types.put(key, type);
            if (previous != null && previous != type) {
                throw new IllegalArgumentException("Structured custom_model_data key '" + key
                        + "' is allocated as both " + previous + " and " + type);
            }
        }
    }
}
