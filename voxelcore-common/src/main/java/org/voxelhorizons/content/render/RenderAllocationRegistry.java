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

        /*
         * A child definition may inherit an explicit numeric CMD from its parent. In that case
         * both content IDs intentionally describe the same visual state and may share the number,
         * but only while an active definition claims that number for the same model. A number held
         * only by tombstones stays reserved and cannot be silently reused by a new content ID.
         */
        Map<Integer, String> activeClaims = new HashMap<Integer, String>();
        for (Map.Entry<ContentID, RenderAllocation> entry : previous.allocations.entrySet()) {
            ItemDefinition definition = items.entries().get(entry.getKey());
            String currentModel = modelOf(definition);
            if (currentModel == null) continue;
            int value = entry.getValue().customModelData();
            String claimedModel = activeClaims.get(Integer.valueOf(value));
            if (claimedModel != null && !claimedModel.equals(currentModel)) {
                throw new IllegalArgumentException("Custom model data " + value
                        + " is claimed by multiple active render models: " + claimedModel + " and " + currentModel);
            }
            activeClaims.put(Integer.valueOf(value), currentModel);
        }

        List<ItemDefinition> definitions = new ArrayList<ItemDefinition>(items.entries().values());
        Collections.sort(definitions, new Comparator<ItemDefinition>() {
            @Override public int compare(ItemDefinition left, ItemDefinition right) {
                return left.id().toString().compareTo(right.id().toString());
            }
        });

        for (ItemDefinition definition : definitions) {
            String model = modelOf(definition);
            if (model == null) continue;

            ItemRenderDefinition render = definition.render();
            CustomModelDataDefinition authored = render.customModelData();
            Integer explicit = authored != null && authored.isNumeric() ? authored.numeric() : null;
            RenderAllocation existing = next.get(definition.id());

            if (existing != null) {
                if (explicit != null && explicit.intValue() != existing.customModelData()) {
                    throw new IllegalArgumentException("Item " + definition.id() + " requests custom_model_data " + explicit
                            + " but stable allocation manifest already reserves " + existing.customModelData());
                }
                String claimedModel = activeClaims.get(Integer.valueOf(existing.customModelData()));
                if (claimedModel != null && !claimedModel.equals(model)) {
                    throw new IllegalArgumentException("Custom model data " + existing.customModelData()
                            + " cannot render both " + claimedModel + " and " + model);
                }
                activeClaims.put(Integer.valueOf(existing.customModelData()), model);
                next.put(definition.id(), existing.withModelAndActive(model, true));
                continue;
            }

            int allocated;
            if (explicit != null) {
                if (explicit.intValue() < 0) throw new IllegalArgumentException("Custom model data cannot be negative for " + definition.id());
                allocated = explicit.intValue();
                if (reserved.contains(Integer.valueOf(allocated))) {
                    String claimedModel = activeClaims.get(Integer.valueOf(allocated));
                    if (claimedModel == null || !claimedModel.equals(model)) {
                        throw new IllegalArgumentException("Custom model data " + allocated + " is already reserved by another content ID");
                    }
                } else {
                    reserved.add(Integer.valueOf(allocated));
                }
                activeClaims.put(Integer.valueOf(allocated), model);
            } else {
                allocated = Math.max(FIRST_AUTO_CUSTOM_MODEL_DATA, nextValue);
                while (reserved.contains(Integer.valueOf(allocated))) allocated++;
                reserved.add(Integer.valueOf(allocated));
                activeClaims.put(Integer.valueOf(allocated), model);
            }

            nextValue = Math.max(nextValue, allocated + 1);
            next.put(definition.id(), new RenderAllocation(allocated, model, true));
        }

        return new RenderAllocationRegistry(nextValue, next);
    }

    private static String modelOf(ItemDefinition definition) {
        if (definition == null) return null;
        ItemRenderDefinition render = definition.render();
        if (render == null || render.model() == null || render.model().trim().isEmpty()) return null;
        return render.model().trim().toLowerCase(java.util.Locale.ROOT);
    }
}
