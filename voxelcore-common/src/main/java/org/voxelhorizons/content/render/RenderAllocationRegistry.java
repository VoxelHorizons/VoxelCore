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
    private final Map<String, StructuredModelDataAllocation> structuredModels;

    public RenderAllocationRegistry(int nextCustomModelData, Map<ContentID, RenderAllocation> allocations) {
        this(nextCustomModelData, allocations, Collections.<String, StructuredModelDataAllocation>emptyMap());
    }

    public RenderAllocationRegistry(int nextCustomModelData,
                                    Map<ContentID, RenderAllocation> allocations,
                                    Map<String, StructuredModelDataAllocation> structuredModels) {
        this.nextCustomModelData = Math.max(FIRST_AUTO_CUSTOM_MODEL_DATA, nextCustomModelData);
        this.allocations = Collections.unmodifiableMap(new LinkedHashMap<ContentID, RenderAllocation>(allocations));
        this.structuredModels = Collections.unmodifiableMap(
                new LinkedHashMap<String, StructuredModelDataAllocation>(structuredModels));
    }

    public static RenderAllocationRegistry empty() {
        return new RenderAllocationRegistry(FIRST_AUTO_CUSTOM_MODEL_DATA,
                Collections.<ContentID, RenderAllocation>emptyMap(),
                Collections.<String, StructuredModelDataAllocation>emptyMap());
    }

    public int nextCustomModelData() { return nextCustomModelData; }
    public Map<ContentID, RenderAllocation> entries() { return allocations; }
    public Map<String, StructuredModelDataAllocation> structuredModels() { return structuredModels; }
    public Optional<RenderAllocation> get(ContentID id) { return Optional.ofNullable(allocations.get(id)); }
    public Optional<StructuredModelDataAllocation> getStructuredModel(String model) {
        return Optional.ofNullable(structuredModels.get(normalizeModel(model)));
    }

    public static RenderAllocationRegistry reconcile(ItemDefinitionRegistry items, RenderAllocationRegistry previous) {
        if (items == null) throw new IllegalArgumentException("items cannot be null");
        if (previous == null) previous = empty();

        Map<ContentID, RenderAllocation> next = new HashMap<ContentID, RenderAllocation>();
        for (Map.Entry<ContentID, RenderAllocation> entry : previous.allocations.entrySet()) {
            next.put(entry.getKey(), entry.getValue().withModelAndActive(entry.getValue().model(), false));
        }
        Map<String, StructuredModelDataAllocation> nextStructured =
                new LinkedHashMap<String, StructuredModelDataAllocation>(previous.structuredModels);

        Set<Integer> reserved = new HashSet<Integer>();
        for (RenderAllocation allocation : previous.allocations.values()) reserved.add(allocation.customModelData());
        int nextValue = previous.nextCustomModelData;

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

            if (authored != null && authored.isStructured()) {
                StructuredModelDataAllocation structured = nextStructured.get(model);
                if (structured == null) structured = StructuredModelDataAllocation.empty();
                nextStructured.put(model, structured.reconcile(authored));
            }

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

        Map<ContentID, RenderAllocation> enriched = new HashMap<ContentID, RenderAllocation>();
        for (Map.Entry<ContentID, RenderAllocation> entry : next.entrySet()) {
            RenderAllocation allocation = entry.getValue();
            StructuredModelDataAllocation structured = nextStructured.get(allocation.model());
            enriched.put(entry.getKey(), allocation.withStructuredModelData(
                    structured == null ? StructuredModelDataAllocation.empty() : structured));
        }
        return new RenderAllocationRegistry(nextValue, enriched, nextStructured);
    }

    private static String modelOf(ItemDefinition definition) {
        if (definition == null) return null;
        ItemRenderDefinition render = definition.render();
        if (render == null || render.model() == null || render.model().trim().isEmpty()) return null;
        return normalizeModel(render.model());
    }

    private static String normalizeModel(String model) {
        if (model == null) return null;
        return model.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
