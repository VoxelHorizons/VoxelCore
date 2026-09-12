package org.voxelhorizons.content.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Version-independent custom model data authored by content packs. */
public final class CustomModelDataDefinition {

    public enum ValueType { FLOAT, FLAG, STRING, COLOR }

    public static final class Value {
        private final ValueType type;
        private final Object value;

        private Value(ValueType type, Object value) {
            this.type = type;
            this.value = value;
        }

        public static Value floating(float value) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw new IllegalArgumentException("Custom model data float must be finite");
            }
            return new Value(ValueType.FLOAT, Float.valueOf(value));
        }

        public static Value flag(boolean value) { return new Value(ValueType.FLAG, Boolean.valueOf(value)); }
        public static Value string(String value) {
            if (value == null) throw new IllegalArgumentException("Custom model data string cannot be null");
            return new Value(ValueType.STRING, value);
        }
        public static Value color(int rgb) {
            if (rgb < 0 || rgb > 0xFFFFFF) throw new IllegalArgumentException("Color must be 24-bit RGB");
            return new Value(ValueType.COLOR, Integer.valueOf(rgb));
        }

        public ValueType type() { return type; }
        public float floatValue() { return ((Float) value).floatValue(); }
        public boolean booleanValue() { return ((Boolean) value).booleanValue(); }
        public String stringValue() { return (String) value; }
        public int colorRgb() { return ((Integer) value).intValue(); }
    }

    private final Integer numeric;
    private final Map<String, Value> structured;

    private CustomModelDataDefinition(Integer numeric, Map<String, Value> structured) {
        this.numeric = numeric;
        this.structured = structured == null
                ? Collections.<String, Value>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Value>(structured));
    }

    public static CustomModelDataDefinition numeric(int value) {
        if (value < 0) throw new IllegalArgumentException("Custom model data cannot be negative");
        return new CustomModelDataDefinition(Integer.valueOf(value), null);
    }

    public static CustomModelDataDefinition structured(Map<String, Value> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Structured custom model data cannot be empty");
        }
        return new CustomModelDataDefinition(null, values);
    }

    public boolean isNumeric() { return numeric != null; }
    public boolean isStructured() { return numeric == null; }
    public Integer numeric() { return numeric; }
    public Map<String, Value> structuredValues() { return structured; }

    public List<Map.Entry<String, Value>> valuesOfType(final ValueType type) {
        List<Map.Entry<String, Value>> values = new ArrayList<Map.Entry<String, Value>>();
        for (Map.Entry<String, Value> entry : structured.entrySet()) {
            if (entry.getValue().type() == type) values.add(entry);
        }
        Collections.sort(values, new Comparator<Map.Entry<String, Value>>() {
            @Override public int compare(Map.Entry<String, Value> left, Map.Entry<String, Value> right) {
                return left.getKey().compareTo(right.getKey());
            }
        });
        return Collections.unmodifiableList(values);
    }
}
