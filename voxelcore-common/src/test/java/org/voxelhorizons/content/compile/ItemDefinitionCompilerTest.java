package org.voxelhorizons.content.compile;

import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.CustomModelDataDefinition;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.item.ItemType;
import org.voxelhorizons.content.item.RawItemDefinition;
import org.voxelhorizons.content.item.RawItemRenderDefinition;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ItemDefinitionCompilerTest {
    private final ItemDefinitionCompiler compiler = new ItemDefinitionCompiler();

    @Test public void compilesPlainItemWithDefaults() {
        ContentID id = id("plain");
        ItemDefinition result = compiler.compile(Collections.singletonList(raw(id, null, null, "minecraft:paper", "Plain", null, null, null, null))).get(id).get();
        assertEquals(ItemType.ITEM, result.type()); assertEquals("minecraft:paper", result.material()); assertFalse(result.bound()); assertTrue(result.lore().isEmpty());
    }

    @Test public void resolvesMultiLevelInheritance() {
        ContentID baseId = id("base"), childId = id("child"), grandchildId = id("grandchild");
        RawItemDefinition base = raw(baseId, null, ItemType.ITEM, "minecraft:paper", "Base", Arrays.asList("base lore"), Boolean.TRUE,
                new RawItemRenderDefinition("voxelhorizons:base", CustomModelDataDefinition.numeric(10)), null);
        RawItemDefinition child = raw(childId, baseId, null, null, "Child", null, null,
                new RawItemRenderDefinition(null, CustomModelDataDefinition.numeric(20)), null);
        RawItemDefinition grandchild = raw(grandchildId, childId, null, null, "Grandchild", null, null,
                new RawItemRenderDefinition("voxelhorizons:grandchild", null), null);
        ItemDefinition result = compiler.compile(Arrays.asList(base, child, grandchild)).get(grandchildId).get();
        assertEquals("minecraft:paper", result.material()); assertEquals("Grandchild", result.displayName());
        assertEquals(Arrays.asList("base lore"), result.lore()); assertTrue(result.bound());
        assertEquals("voxelhorizons:grandchild", result.render().model());
        assertEquals(Integer.valueOf(20), result.render().customModelData().numeric());
    }

    @Test public void deepMergesStructuredCustomModelData() {
        ContentID baseId = id("base_cmd"), childId = id("child_cmd");
        Map<String, CustomModelDataDefinition.Value> parentValues = new LinkedHashMap<String, CustomModelDataDefinition.Value>();
        parentValues.put("variant", CustomModelDataDefinition.Value.string("base"));
        parentValues.put("powered", CustomModelDataDefinition.Value.flag(false));
        Map<String, CustomModelDataDefinition.Value> childValues = new LinkedHashMap<String, CustomModelDataDefinition.Value>();
        childValues.put("powered", CustomModelDataDefinition.Value.flag(true));
        childValues.put("intensity", CustomModelDataDefinition.Value.floating(0.5f));
        RawItemDefinition base = raw(baseId, null, null, "minecraft:paper", null, null, null,
                new RawItemRenderDefinition("voxelhorizons:item/base", CustomModelDataDefinition.structured(parentValues)), null);
        RawItemDefinition child = raw(childId, baseId, null, null, null, null, null,
                new RawItemRenderDefinition(null, CustomModelDataDefinition.structured(childValues)), null);
        CustomModelDataDefinition result = compiler.compile(Arrays.asList(base, child)).get(childId).get().render().customModelData();
        assertEquals("base", result.structuredValues().get("variant").stringValue());
        assertTrue(result.structuredValues().get("powered").booleanValue());
        assertEquals(0.5f, result.structuredValues().get("intensity").floatValue(), 0.0001f);
    }

    @Test public void explicitFalseOverridesInheritedTrue() {
        ContentID baseId = id("base_bound"), childId = id("child_bound");
        ItemDefinition result = compiler.compile(Arrays.asList(
                raw(baseId, null, null, "minecraft:paper", null, null, Boolean.TRUE, null, null),
                raw(childId, baseId, null, null, null, null, Boolean.FALSE, null, null))).get(childId).get();
        assertFalse(result.bound());
    }

    @Test public void abstractDefinitionsMayOmitMaterialAndStateIsInherited() {
        ContentID baseId = id("abstract_base"), childId = id("abstract_child");
        RawItemDefinition base = new RawItemDefinition(baseId, null, null, null, null, null,
                Boolean.FALSE, Boolean.TRUE, null, null);
        RawItemDefinition child = new RawItemDefinition(childId, baseId, null, "minecraft:paper", null, null,
                null, Boolean.FALSE, null, null);
        ItemDefinitionRegistry registry = compiler.compile(Arrays.asList(base, child));
        assertTrue(registry.get(baseId).get().abstractDefinition());
        assertFalse(registry.get(childId).get().abstractDefinition());
        assertFalse(registry.get(childId).get().bound());
    }

    @Test public void concreteDefinitionsStillRequireMaterial() {
        RawItemDefinition definition = new RawItemDefinition(id("concrete_no_material"), null, null, null,
                null, null, null, Boolean.FALSE, null, null);
        expectCompileFailure(Collections.singletonList(definition), "has no material");
    }

    @Test public void childListReplacesParentList() {
        ContentID baseId = id("base_lore"), childId = id("child_lore");
        RawItemDefinition base = raw(baseId, null, null, "minecraft:paper", null, Arrays.asList("one", "two"), null, null, null);
        RawItemDefinition child = raw(childId, baseId, null, null, null, Collections.singletonList("child"), null, null, null);
        assertEquals(Collections.singletonList("child"), compiler.compile(Arrays.asList(base, child)).get(childId).get().lore());
    }

    @SuppressWarnings("unchecked")
    @Test public void deepMergesPropertyMaps() {
        ContentID baseId = id("base_props"), childId = id("child_props");
        Map<String, Object> parentFurniture = new HashMap<String, Object>(); parentFurniture.put("seats", 1); parentFurniture.put("storage", false);
        Map<String, Object> parentProperties = new HashMap<String, Object>(); parentProperties.put("furniture", parentFurniture);
        Map<String, Object> childFurniture = new HashMap<String, Object>(); childFurniture.put("storage", true);
        Map<String, Object> childProperties = new HashMap<String, Object>(); childProperties.put("furniture", childFurniture);
        Map<String, Object> furniture = (Map<String, Object>) compiler.compile(Arrays.asList(
                raw(baseId, null, null, "minecraft:paper", null, null, null, null, parentProperties),
                raw(childId, baseId, null, null, null, null, null, null, childProperties)))
                .get(childId).get().properties().get("furniture");
        assertEquals(1, ((Number) furniture.get("seats")).intValue()); assertEquals(Boolean.TRUE, furniture.get("storage"));
    }

    @Test public void rejectsMissingParent() { expectCompileFailure(Collections.singletonList(raw(id("orphan"), id("missing"), null, null, null, null, null, null, null)), "missing parent"); }
    @Test public void rejectsSelfCycle() { ContentID value = id("self"); expectCompileFailure(Collections.singletonList(raw(value, value, null, "minecraft:paper", null, null, null, null, null)), "Circular item inheritance detected"); }
    @Test public void rejectsMultiItemCycle() {
        ContentID a = id("cycle_a"), b = id("cycle_b"), c = id("cycle_c");
        expectCompileFailure(Arrays.asList(raw(a,b,null,"minecraft:paper",null,null,null,null,null), raw(b,c,null,null,null,null,null,null,null), raw(c,a,null,null,null,null,null,null,null)), "cycle_a");
    }
    @Test public void rejectsDuplicateIds() {
        ContentID duplicate = id("duplicate");
        expectCompileFailure(Arrays.asList(raw(duplicate,null,null,"minecraft:paper",null,null,null,null,null), raw(duplicate,null,null,"minecraft:stone",null,null,null,null,null)), "Duplicate item id");
    }
    @Test public void rejectsMissingMaterialAfterInheritance() { expectCompileFailure(Collections.singletonList(raw(id("no_material"), null, null, null, null, null, null, null, null)), "has no material"); }

    private void expectCompileFailure(java.util.Collection<RawItemDefinition> definitions, String expectedText) {
        try { compiler.compile(definitions); fail("Expected compilation to fail"); }
        catch (ContentCompileException exception) { assertTrue(exception.getMessage().contains(expectedText)); }
    }
    private ContentID id(String value) { return ContentID.of("voxelhorizons", value); }
    private RawItemDefinition raw(ContentID id, ContentID parent, ItemType type, String material, String displayName,
                                  java.util.List<String> lore, Boolean bound, RawItemRenderDefinition render, Map<String,Object> properties) {
        return new RawItemDefinition(id, parent, type, material, displayName, lore, bound, render, properties);
    }
}
