package org.voxelhorizons.content.render;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.CustomModelDataDefinition;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.item.ItemRenderDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Immutable stable render allocations shared by pack generation and runtime item creation. */
public final class RenderAllocationRegistry {
    public static final int FIRST_AUTO_CUSTOM_MODEL_DATA = 1000;

    private final int nextCustomModelData;
    private final Map<ContentID, RenderAllocation> allocations;

    public RenderAllocationRegistry(int nextCustomModelData, Map<ContentID, RenderAllocation> allocations) {
        this.nextCustomModelData = Math.max(FIRST_AUTO_CUSTOM_MODEL_DATA, nextCustomModelData);
        this.allocations = Collections.unmodifiableMap(new LinkedHashMap<ContentID, RenderAllocation>(allocations));
    }

    public static RenderAllocationRegistry empty() {
        return new RenderAllocationRegistry(FIRST_AUTO_CUSTOM_MODEL_DATA,
                Collections.<ContentID, RenderAllocation>emptyMap());
    }

    public int nextCustomModelData() { return nextCustomModelData; }
    public Map<ContentID, RenderAllocation> entries() { return allocations; }
    public Optional<RenderAllocation> get(ContentID id) { return Optional.ofNullable(allocations.get(id)); }

    public static RenderAllocationRegistry reconcile(ItemDefinitionRegistry items, RenderAllocationRegistry previous) {
        if (items == null) throw new IllegalArgumentException("items cannot be null");
        if (previous == null) previous = empty();

        Map<ContentID, RenderAllocation> next = new HashMap<ContentID, RenderAllocation>();
        for (Map.Entry<ContentID, RenderAllocation> entry : previous.allocations.entrySet()) {
            next.put(entry.getKey(), entry.getValue().withModelAndActive(entry.getValue().model(), false));
        }

        Set<Integer> reserved = new HashSet<Integer>();
        for (RenderAllocation allocation : previous.allocations.values()) reserved.add(allocation.customModelData());
        int nextValue = previous.nextCustomModelData;

        List<ItemDefinition> definitions = new ArrayList<ItemDefinition>(items.entries().values());
        Collections.sort(definitions, new Comparator<ItemDefinition>() {
            @Override public int compare(ItemDefinition left, ItemDefinition right) {
                return left.id().toString().compareTo(right.id().toString());
            }
        });

        for (ItemDefinition definition : definitions) {
            ItemRenderDefinition render = definition.render();
            if (render == null || render.model() == null || render.model().trim().isEmpty()) continue;

            String model = render.model().trim().toLowerCase(java.util.Locale.ROOT);
            CustomModelDataDefinition authored = render.customModelData();
            Integer explicit = authored != null && authored.isNumeric() ? authored.numeric() : null;
            RenderAllocation existing = next.get(definition.id());

            if (existing != null) {
                if (explicit != null && explicit.intValue() != existing.customModelData()) {
                    throw new IllegalArgumentException("Item " + definition.id() + " requests custom_model_data " + explicit
                            + " but stable allocation manifest already reserves " + existing.customModelData());
                }
                next.put(definition.id(), existing.withModelAndActive(model, true));
                continue;
            }

            int allocated;
            if (explicit != null) {
                if (explicit.intValue() < 0) throw new IllegalArgumentException("Custom model data cannot be negative for " + definition.id());
                allocated = explicit.intValue();
                if (reserved.contains(allocated)) {
                    throw new IllegalArgumentException("Custom model data " + allocated + " is already reserved by another content ID");
                }
            } else {
                allocated = Math.max(FIRST_AUTO_CUSTOM_MODEL_DATA, nextValue);
                while (reserved.contains(allocated)) allocated++;
            }

            reserved.add(allocated);
            nextValue = Math.max(nextValue, allocated + 1);
            next.put(definition.id(), new RenderAllocation(allocated, model, true));
        }

        return new RenderAllocationRegistry(nextValue, next);
    }
}
