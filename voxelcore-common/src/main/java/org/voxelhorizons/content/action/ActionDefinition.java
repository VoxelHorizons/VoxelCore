package org.voxelhorizons.content.action;

import org.voxelhorizons.content.item.ImmutableData;

import java.util.Map;

/** Immutable, platform-neutral action compiled from authored content. */
public final class ActionDefinition {
    private final ActionType type;
    private final Map<String, Object> parameters;

    public ActionDefinition(ActionType type, Map<String, Object> parameters) {
        if (type == null) throw new IllegalArgumentException("Action type cannot be null");
        this.type = type;
        this.parameters = ImmutableData.map(parameters);
    }

    public ActionType type() { return type; }
    public Map<String, Object> parameters() { return parameters; }

    public String string(String key) {
        Object value = parameters.get(key);
        return value instanceof String ? (String) value : null;
    }

    public boolean booleanValue(String key, boolean fallback) {
        Object value = parameters.get(key);
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }

    public int integer(String key, int fallback) {
        Object value = parameters.get(key);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }
}
