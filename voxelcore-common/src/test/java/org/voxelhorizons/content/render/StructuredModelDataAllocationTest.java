package org.voxelhorizons.content.render;

import org.junit.Test;
import org.voxelhorizons.content.item.CustomModelDataDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StructuredModelDataAllocationTest {

    @Test
    public void preservesExistingIndicesWhenNewKeysAreAdded() {
        Map<String, CustomModelDataDefinition.Value> firstValues = new LinkedHashMap<String, CustomModelDataDefinition.Value>();
        firstValues.put("intensity", CustomModelDataDefinition.Value.floating(0.75f));
        firstValues.put("powered", CustomModelDataDefinition.Value.flag(true));
        StructuredModelDataAllocation first = StructuredModelDataAllocation.empty()
                .reconcile(CustomModelDataDefinition.structured(firstValues));

        assertEquals(Integer.valueOf(0), first.index(CustomModelDataDefinition.ValueType.FLOAT, "intensity").get());
        assertEquals(Integer.valueOf(0), first.index(CustomModelDataDefinition.ValueType.FLAG, "powered").get());

        Map<String, CustomModelDataDefinition.Value> secondValues = new LinkedHashMap<String, CustomModelDataDefinition.Value>();
        secondValues.put("speed", CustomModelDataDefinition.Value.floating(1.0f));
        secondValues.put("intensity", CustomModelDataDefinition.Value.floating(0.25f));
        StructuredModelDataAllocation second = first.reconcile(CustomModelDataDefinition.structured(secondValues));

        assertEquals(Integer.valueOf(0), second.index(CustomModelDataDefinition.ValueType.FLOAT, "intensity").get());
        assertEquals(Integer.valueOf(1), second.index(CustomModelDataDefinition.ValueType.FLOAT, "speed").get());
        assertEquals(Integer.valueOf(0), second.index(CustomModelDataDefinition.ValueType.FLAG, "powered").get());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsChangingSemanticKeyType() {
        Map<String, CustomModelDataDefinition.Value> firstValues = new LinkedHashMap<String, CustomModelDataDefinition.Value>();
        firstValues.put("state", CustomModelDataDefinition.Value.string("red"));
        StructuredModelDataAllocation first = StructuredModelDataAllocation.empty()
                .reconcile(CustomModelDataDefinition.structured(firstValues));

        Map<String, CustomModelDataDefinition.Value> changed = new LinkedHashMap<String, CustomModelDataDefinition.Value>();
        changed.put("state", CustomModelDataDefinition.Value.flag(true));
        first.reconcile(CustomModelDataDefinition.structured(changed));
    }

    @Test
    public void migratesSchemaTwoManifestAndWritesSchemaThree() throws Exception {
        java.nio.file.Path file = java.nio.file.Files.createTempFile("voxelcore-render", ".yml");
        java.nio.file.Files.write(file, ("schema: 2\n" +
                "next_custom_model_data: 1001\n" +
                "allocations:\n" +
                "  'test:ruby':\n" +
                "    custom_model_data: 1000\n" +
                "    model: 'test:item/ruby'\n" +
                "    active: true\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));

        RenderAllocationStore store = new RenderAllocationStore(file);
        RenderAllocationRegistry loaded = store.load();
        assertEquals(1000, loaded.get(org.voxelhorizons.content.ContentID.of("test", "ruby")).get().customModelData());
        assertTrue(loaded.structuredModels().isEmpty());

        store.save(loaded);
        String saved = new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(saved.contains("schema: 3"));
        assertTrue(saved.contains("structured_model_data:"));
    }
}
