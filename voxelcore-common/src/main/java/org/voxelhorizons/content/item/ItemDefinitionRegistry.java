package org.voxelhorizons.content.item;

import org.voxelhorizons.content.ContentID;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ItemDefinitionRegistry {

    private final Map<ContentID, ItemDefinition> definitions;

    public ItemDefinitionRegistry(Map<ContentID, ItemDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(new HashMap<ContentID, ItemDefinition>(definitions));
    }

    public Optional<ItemDefinition> get(ContentID id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public boolean contains(ContentID id) {
        return definitions.containsKey(id);
    }

    public Map<ContentID, ItemDefinition> entries() {
        return definitions;
    }

    public int size() {
        return definitions.size();
    }
}
