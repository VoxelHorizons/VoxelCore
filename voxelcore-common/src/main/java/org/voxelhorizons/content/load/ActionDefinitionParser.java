package org.voxelhorizons.content.load;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.action.ActionDefinition;
import org.voxelhorizons.content.action.ActionType;
import org.voxelhorizons.content.action.EventActions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict parser for reusable item and block event actions. */
final class ActionDefinitionParser {
    private static final Set<String> SET_BLOCK_KEYS = keys("type", "block", "target", "consume", "replace");
    private static final Set<String> REMOVE_BLOCK_KEYS = keys("type", "target");
    private static final Set<String> COMMAND_KEYS = keys("type", "command", "executor");
    private static final Set<String> ITEM_KEYS = keys("type", "item", "amount");
    private static final Set<String> MESSAGE_KEYS = keys("type", "text");
    private static final Set<String> CANCEL_KEYS = keys("type");

    EventActions parse(Object raw, ContentID owner, Path file) {
        if (raw == null) return EventActions.empty();
        if (!(raw instanceof Map)) throw error("events must be a mapping", owner, file);
        Map<String, List<ActionDefinition>> events = new LinkedHashMap<String, List<ActionDefinition>>();
        flatten((Map<?, ?>) raw, "", events, owner, file);
        return new EventActions(events);
    }

    private void flatten(Map<?, ?> node, String path, Map<String, List<ActionDefinition>> out,
                         ContentID owner, Path file) {
        if (node.containsKey("actions")) {
            if (node.size() != 1) throw error("event leaf '" + path + "' may only contain actions", owner, file);
            out.put(path, parseActions(node.get("actions"), owner, file, path));
            return;
        }
        for (Map.Entry<?, ?> entry : node.entrySet()) {
            if (!(entry.getKey() instanceof String) || ((String) entry.getKey()).trim().isEmpty()) {
                throw error("event names must be non-empty strings", owner, file);
            }
            String child = path.isEmpty() ? normalize((String) entry.getKey())
                    : path + "." + normalize((String) entry.getKey());
            Object value = entry.getValue();
            if (value instanceof List) out.put(child, parseActions(value, owner, file, child));
            else if (value instanceof Map) flatten((Map<?, ?>) value, child, out, owner, file);
            else throw error("event '" + child + "' must contain an action list or nested events", owner, file);
        }
    }

    private List<ActionDefinition> parseActions(Object raw, ContentID owner, Path file, String event) {
        if (!(raw instanceof List)) throw error("actions for '" + event + "' must be a list", owner, file);
        List<ActionDefinition> actions = new ArrayList<ActionDefinition>();
        for (Object entry : (List<?>) raw) {
            if (!(entry instanceof Map)) throw error("each action for '" + event + "' must be a mapping", owner, file);
            Map<?, ?> map = (Map<?, ?>) entry;
            Object typeValue = map.get("type");
            if (!(typeValue instanceof String)) throw error("action type must be a string", owner, file);
            final ActionType type;
            try { type = ActionType.parse((String) typeValue); }
            catch (IllegalArgumentException exception) { throw error(exception.getMessage(), owner, file); }
            validateKeys(map, allowed(type), owner, file);
            Map<String, Object> parameters = normalize(map, owner, file);
            parameters.remove("type");
            validateRequired(type, parameters, owner, file);
            actions.add(new ActionDefinition(type, parameters));
        }
        if (actions.isEmpty()) throw error("actions for '" + event + "' cannot be empty", owner, file);
        return actions;
    }

    private static void validateRequired(ActionType type, Map<String, Object> values, ContentID owner, Path file) {
        String required = null;
        if (type == ActionType.SET_BLOCK) required = "block";
        else if (type == ActionType.COMMAND) required = "command";
        else if (type == ActionType.GIVE_ITEM || type == ActionType.DROP_ITEM) required = "item";
        else if (type == ActionType.MESSAGE) required = "text";
        if (required != null && (!(values.get(required) instanceof String)
                || ((String) values.get(required)).trim().isEmpty())) {
            throw error(type.name().toLowerCase(java.util.Locale.ROOT) + " requires '" + required + "'", owner, file);
        }
        Object amount = values.get("amount");
        if (amount != null && (!(amount instanceof Number) || ((Number) amount).intValue() < 1)) {
            throw error("action amount must be a positive integer", owner, file);
        }
        Object consume = values.get("consume");
        if (consume != null && (!(consume instanceof Number) || ((Number) consume).intValue() < 0)) {
            throw error("set_block consume must be a non-negative integer", owner, file);
        }
    }

    private static Set<String> allowed(ActionType type) {
        switch (type) {
            case SET_BLOCK: return SET_BLOCK_KEYS;
            case REMOVE_BLOCK: return REMOVE_BLOCK_KEYS;
            case COMMAND: return COMMAND_KEYS;
            case GIVE_ITEM:
            case DROP_ITEM: return ITEM_KEYS;
            case MESSAGE: return MESSAGE_KEYS;
            default: return CANCEL_KEYS;
        }
    }

    private static void validateKeys(Map<?, ?> map, Set<String> allowed, ContentID owner, Path file) {
        for (Object key : map.keySet()) {
            if (!(key instanceof String) || !allowed.contains(key)) {
                throw error("unsupported action key '" + key + "'", owner, file);
            }
        }
    }

    private static Map<String, Object> normalize(Map<?, ?> map, ContentID owner, Path file) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String)) throw error("action keys must be strings", owner, file);
            Object value = entry.getValue();
            if (!(value instanceof String) && !(value instanceof Number) && !(value instanceof Boolean)) {
                throw error("action value for '" + entry.getKey() + "' must be scalar", owner, file);
            }
            result.put((String) entry.getKey(), value);
        }
        return result;
    }

    private static Set<String> keys(String... values) {
        return new HashSet<String>(Arrays.asList(values));
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static ContentLoadException error(String message, ContentID owner, Path file) {
        return new ContentLoadException(message + " for " + owner + " in " + file);
    }
}
