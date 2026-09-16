package org.voxelhorizons.content.block;

import org.voxelhorizons.content.ContentID;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class BlockDefinitionRegistry {
    private static final BlockDefinitionRegistry EMPTY = new BlockDefinitionRegistry(Collections.<ContentID, BlockDefinition>emptyMap());
    private final Map<ContentID, BlockDefinition> definitions;

    public BlockDefinitionRegistry(Map<ContentID, BlockDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<ContentID, BlockDefinition>(definitions));
    }

    public static BlockDefinitionRegistry empty() { return EMPTY; }
    public Optional<BlockDefinition> get(ContentID id) { return Optional.ofNullable(definitions.get(id)); }
    public boolean contains(ContentID id) { return definitions.containsKey(id); }
    public int size() { return definitions.size(); }
    public Map<ContentID, BlockDefinition> entries() { return definitions; }
}
