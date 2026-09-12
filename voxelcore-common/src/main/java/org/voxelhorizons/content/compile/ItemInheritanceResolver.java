package org.voxelhorizons.content.compile;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.CustomModelDataDefinition;
import org.voxelhorizons.content.item.RawItemDefinition;
import org.voxelhorizons.content.item.RawItemRenderDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves single-parent item inheritance with DFS cycle detection. */
public final class ItemInheritanceResolver {

    private enum State { VISITING, RESOLVED }

    public Map<ContentID, RawItemDefinition> resolveAll(Map<ContentID, RawItemDefinition> definitions) {
        Map<ContentID, RawItemDefinition> resolved = new LinkedHashMap<ContentID, RawItemDefinition>();
        Map<ContentID, State> states = new HashMap<ContentID, State>();
        Deque<ContentID> path = new ArrayDeque<ContentID>();
        for (ContentID id : definitions.keySet()) resolve(id, definitions, resolved, states, path);
        return resolved;
    }

    private RawItemDefinition resolve(ContentID id,
                                      Map<ContentID, RawItemDefinition> definitions,
                                      Map<ContentID, RawItemDefinition> resolved,
                                      Map<ContentID, State> states,
                                      Deque<ContentID> path) {
        State state = states.get(id);
        if (state == State.RESOLVED) return resolved.get(id);
        if (state == State.VISITING) throw cycleException(id, path);
        RawItemDefinition child = definitions.get(id);
        if (child == null) throw new ContentCompileException("Missing item definition: " + id);
        states.put(id, State.VISITING);
        path.addLast(id);
        RawItemDefinition result = child;
        if (child.parent() != null) {
            RawItemDefinition parent = definitions.get(child.parent());
            if (parent == null) {
                throw new ContentCompileException("Unable to compile " + id + ": missing parent " + child.parent()
                        + " (dependency chain: " + formatPath(path) + ")");
            }
            result = merge(resolve(child.parent(), definitions, resolved, states, path), child);
        }
        path.removeLast();
        states.put(id, State.RESOLVED);
        resolved.put(id, result);
        return result;
    }

    private RawItemDefinition merge(RawItemDefinition parent, RawItemDefinition child) {
        return new RawItemDefinition(
                child.id(), child.parent(),
                child.type() != null ? child.type() : parent.type(),
                child.material() != null ? child.material() : parent.material(),
                child.displayName() != null ? child.displayName() : parent.displayName(),
                child.lore() != null ? child.lore() : parent.lore(),
                child.bound() != null ? child.bound() : parent.bound(),
                mergeRender(parent.render(), child.render()),
                mergeMaps(parent.properties(), child.properties())
        );
    }

    private RawItemRenderDefinition mergeRender(RawItemRenderDefinition parent, RawItemRenderDefinition child) {
        if (parent == null) return child;
        if (child == null) return parent;
        return new RawItemRenderDefinition(
                child.model() != null ? child.model() : parent.model(),
                child.unbreakable() != null ? child.unbreakable() : parent.unbreakable(),
                child.durability() != null ? child.durability() : parent.durability(),
                mergeBooleanMaps(parent.attributes(), child.attributes()),
                mergeCustomModelData(parent.customModelData(), child.customModelData())
        );
    }

    private Map<String, Boolean> mergeBooleanMaps(Map<String, Boolean> parent, Map<String, Boolean> child) {
        if (parent == null && child == null) return null;
        Map<String, Boolean> merged = new LinkedHashMap<String, Boolean>();
        if (parent != null) merged.putAll(parent);
        if (child != null) merged.putAll(child);
        return merged;
    }

    private CustomModelDataDefinition mergeCustomModelData(CustomModelDataDefinition parent, CustomModelDataDefinition child) {
        if (parent == null) return child;
        if (child == null) return parent;
        if (!parent.isStructured() || !child.isStructured()) return child;
        Map<String, CustomModelDataDefinition.Value> merged = new LinkedHashMap<String, CustomModelDataDefinition.Value>();
        merged.putAll(parent.structuredValues());
        merged.putAll(child.structuredValues());
        return CustomModelDataDefinition.structured(merged);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeMaps(Map<String, Object> parent, Map<String, Object> child) {
        if (parent == null && child == null) return null;
        if (parent == null) return deepCopyMap(child);
        if (child == null) return deepCopyMap(parent);
        Map<String, Object> merged = deepCopyMap(parent);
        for (Map.Entry<String, Object> entry : child.entrySet()) {
            Object childValue = entry.getValue();
            Object parentValue = merged.get(entry.getKey());
            if (childValue instanceof Map && parentValue instanceof Map) {
                merged.put(entry.getKey(), mergeMaps((Map<String, Object>) parentValue, (Map<String, Object>) childValue));
            } else {
                merged.put(entry.getKey(), deepCopyValue(childValue));
            }
        }
        return merged;
    }

    private Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        if (source == null) return copy;
        for (Map.Entry<String, Object> entry : source.entrySet()) copy.put(entry.getKey(), deepCopyValue(entry.getValue()));
        return copy;
    }

    @SuppressWarnings("unchecked")
    private Object deepCopyValue(Object value) {
        if (value instanceof Map) return deepCopyMap((Map<String, Object>) value);
        if (value instanceof List) return new ArrayList<Object>((List<Object>) value);
        return value;
    }

    private ContentCompileException cycleException(ContentID repeated, Deque<ContentID> path) {
        List<ContentID> cycle = new ArrayList<ContentID>();
        boolean include = false;
        for (ContentID id : path) {
            if (id.equals(repeated)) include = true;
            if (include) cycle.add(id);
        }
        cycle.add(repeated);
        StringBuilder message = new StringBuilder("Circular item inheritance detected: ");
        for (int i = 0; i < cycle.size(); i++) {
            if (i > 0) message.append(" -> ");
            message.append(cycle.get(i));
        }
        return new ContentCompileException(message.toString());
    }

    private String formatPath(Deque<ContentID> path) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (ContentID id : path) {
            if (!first) builder.append(" -> ");
            builder.append(id);
            first = false;
        }
        return builder.toString();
    }
}
