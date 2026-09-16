package org.voxelhorizons.content.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable action lists indexed by normalized event paths such as interact.right. */
public final class EventActions {
    private static final EventActions EMPTY = new EventActions(Collections.<String, List<ActionDefinition>>emptyMap());
    private final Map<String, List<ActionDefinition>> events;

    public EventActions(Map<String, List<ActionDefinition>> events) {
        Map<String, List<ActionDefinition>> copy = new LinkedHashMap<String, List<ActionDefinition>>();
        if (events != null) {
            for (Map.Entry<String, List<ActionDefinition>> entry : events.entrySet()) {
                copy.put(normalize(entry.getKey()), Collections.unmodifiableList(
                        new ArrayList<ActionDefinition>(entry.getValue())));
            }
        }
        this.events = Collections.unmodifiableMap(copy);
    }

    public static EventActions empty() { return EMPTY; }
    public List<ActionDefinition> forEvent(String event) {
        List<ActionDefinition> actions = events.get(normalize(event));
        return actions == null ? Collections.<ActionDefinition>emptyList() : actions;
    }
    public Map<String, List<ActionDefinition>> entries() { return events; }
    public boolean isEmpty() { return events.isEmpty(); }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
