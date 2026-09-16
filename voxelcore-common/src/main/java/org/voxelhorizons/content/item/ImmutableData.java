package org.voxelhorizons.content.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deep immutable copies for YAML-shaped compiled content data. */
public final class ImmutableData {
    private ImmutableData() {}

    public static Map<String, Object> map(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Collections.emptyMap();
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), value(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    @SuppressWarnings("unchecked")
    private static Object value(Object value) {
        if (value instanceof Map) {
            Map<?, ?> raw = (Map<?, ?>) value;
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException("Compiled content map keys must be strings");
                }
                copy.put((String) entry.getKey(), value(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<Object>();
            for (Object element : (List<Object>) value) copy.add(value(element));
            return Collections.unmodifiableList(copy);
        }
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) return value;
        throw new IllegalArgumentException("Unsupported compiled content value type: " + value.getClass().getName());
    }
}
