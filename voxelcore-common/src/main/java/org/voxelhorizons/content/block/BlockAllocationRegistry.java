package org.voxelhorizons.content.block;

import org.voxelhorizons.content.ContentID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class BlockAllocationRegistry {
    private final Map<ContentID, BlockAllocation> allocations;

    public BlockAllocationRegistry(Map<ContentID, BlockAllocation> allocations) {
        this.allocations = Collections.unmodifiableMap(new LinkedHashMap<ContentID, BlockAllocation>(allocations));
    }

    public static BlockAllocationRegistry empty() {
        return new BlockAllocationRegistry(Collections.<ContentID, BlockAllocation>emptyMap());
    }

    public Optional<BlockAllocation> get(ContentID id) { return Optional.ofNullable(allocations.get(id)); }
    public Map<ContentID, BlockAllocation> entries() { return allocations; }

    public Optional<ContentID> identify(String carrierState, boolean modern) {
        if (carrierState == null) return Optional.empty();
        for (Map.Entry<ContentID, BlockAllocation> entry : allocations.entrySet()) {
            if (entry.getValue().active() && carrierState.equals(BlockCarrierStates.state(entry.getValue(), modern))) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public static BlockAllocationRegistry reconcile(BlockDefinitionRegistry blocks,
                                                     BlockAllocationRegistry previous,
                                                     boolean modern) {
        if (previous == null) previous = empty();
        Map<ContentID, BlockAllocation> next = new LinkedHashMap<ContentID, BlockAllocation>();
        Map<BlockMethod, Set<Integer>> reserved = new LinkedHashMap<BlockMethod, Set<Integer>>();
        for (BlockMethod method : BlockMethod.values()) reserved.put(method, new HashSet<Integer>());
        for (Map.Entry<ContentID, BlockAllocation> entry : previous.entries().entrySet()) {
            next.put(entry.getKey(), entry.getValue().withActive(false));
            reserved.get(entry.getValue().method()).add(Integer.valueOf(entry.getValue().slot()));
        }

        List<BlockDefinition> definitions = new ArrayList<BlockDefinition>(blocks.entries().values());
        Collections.sort(definitions, new Comparator<BlockDefinition>() {
            @Override public int compare(BlockDefinition left, BlockDefinition right) {
                return left.id().toString().compareTo(right.id().toString());
            }
        });
        for (BlockDefinition definition : definitions) {
            if (definition.abstractDefinition()) continue;
            BlockMethod requested = definition.method() == BlockMethod.AUTO
                    ? (modern ? BlockMethod.SOLID : BlockMethod.MUSHROOM) : definition.method();
            if (BlockCarrierStates.capacity(requested, modern) == 0) {
                throw new IllegalArgumentException("Block method " + requested.name().toLowerCase(java.util.Locale.ROOT)
                        + " is not supported for " + definition.id() + " on this target");
            }
            BlockAllocation existing = next.get(definition.id());
            if (existing != null) {
                if (definition.method() != BlockMethod.AUTO && existing.method() != requested) {
                    throw new IllegalArgumentException("Block " + definition.id() + " cannot change stable method from "
                            + existing.method() + " to " + requested);
                }
                if (existing.slot() >= BlockCarrierStates.capacity(existing.method(), modern)) {
                    throw new IllegalArgumentException("Stable block allocation for " + definition.id()
                            + " is unsupported on this target");
                }
                next.put(definition.id(), existing.withActive(true));
                continue;
            }
            int slot = 0;
            Set<Integer> used = reserved.get(requested);
            while (used.contains(Integer.valueOf(slot))) slot++;
            if (slot >= BlockCarrierStates.capacity(requested, modern)) {
                throw new IllegalArgumentException("No " + requested.name().toLowerCase(java.util.Locale.ROOT)
                        + " carrier states remain for " + definition.id());
            }
            used.add(Integer.valueOf(slot));
            next.put(definition.id(), new BlockAllocation(requested, slot, true));
        }
        return new BlockAllocationRegistry(next);
    }
}
