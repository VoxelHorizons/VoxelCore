package org.voxelhorizons.content.compile;

import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.action.ActionDefinition;
import org.voxelhorizons.content.action.ActionType;
import org.voxelhorizons.content.action.EventActions;
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


    @SuppressWarnings("unchecked")
    @Test public void childVehiclePropertiesOverrideOnlyAuthoredNestedFields() {
        ContentID baseId=id("tractor"), childId=id("tractor_yellow"), grandchildId=id("tractor_gold");
        Map<String,Object> physics=new LinkedHashMap<String,Object>();
        physics.put("speed",0.28);
        physics.put("braking",0.04);
        Map<String,Object> inventory=new LinkedHashMap<String,Object>();
        inventory.put("enabled",true);
        inventory.put("rows",3);
        inventory.put("preview_item","voxelhorizons:tractor_icon");
        Map<String,Object> chest=new LinkedHashMap<String,Object>();
        chest.put("enabled",true);
        chest.put("preview_item","voxelhorizons:tractor_chest_icon");
        inventory.put("chest",chest);
        Map<String,Object> vehicle=new LinkedHashMap<String,Object>();
        vehicle.put("physics",physics);
        vehicle.put("inventory",inventory);
        vehicle.put("public",true);
        Map<String,Object> baseProps=new LinkedHashMap<String,Object>();
        baseProps.put("vehicle",vehicle);
        Map<String,Object> yellowPhysics=new LinkedHashMap<String,Object>();
        yellowPhysics.put("speed",0.42);
        Map<String,Object> yellowVehicle=new LinkedHashMap<String,Object>();
        yellowVehicle.put("physics",yellowPhysics);
        Map<String,Object> yellowProps=new LinkedHashMap<String,Object>();
        yellowProps.put("vehicle",yellowVehicle);
        Map<String,Object> grandchildVehicle=new LinkedHashMap<String,Object>();
        Map<String,Object> childInventory=new LinkedHashMap<String,Object>();
        childInventory.put("preview_item","voxelhorizons:gold_icon");
        grandchildVehicle.put("inventory",childInventory);
        grandchildVehicle.put("public",false);
        Map<String,Object> grandchildProps=new LinkedHashMap<String,Object>();
        grandchildProps.put("vehicle",grandchildVehicle);

        ItemDefinitionRegistry compiled=compiler.compile(Arrays.asList(
                raw(baseId,null,null,"minecraft:paper","Tractor",null,null,null,baseProps),
                raw(childId,baseId,null,null,"Golden Tractor",null,null,null,yellowProps),
                raw(grandchildId,childId,null,null,null,null,null,null,grandchildProps)));
        Map<String,Object> variant=(Map<String,Object>)compiled.get(grandchildId).get()
                .properties().get("vehicle");
        Map<String,Object> resultingPhysics=(Map<String,Object>)variant.get("physics");
        Map<String,Object> resultingInventory=(Map<String,Object>)variant.get("inventory");
        assertEquals(0.42,((Number)resultingPhysics.get("speed")).doubleValue(),0.001);
        assertEquals(0.04,((Number)resultingPhysics.get("braking")).doubleValue(),0.001);
        assertEquals(Boolean.FALSE,variant.get("public"));
        assertEquals("voxelhorizons:gold_icon",resultingInventory.get("preview_item"));
        assertEquals(3,((Number)resultingInventory.get("rows")).intValue());
        assertEquals("voxelhorizons:tractor_chest_icon",
                ((Map<String,Object>)resultingInventory.get("chest")).get("preview_item"));
        assertEquals("Tractor",compiled.get(baseId).get().displayName());
        assertEquals(0.28,((Number)((Map<String,Object>)((Map<String,Object>)
                compiled.get(baseId).get().properties().get("vehicle")).get("physics"))
                .get("speed")).doubleValue(),0.001);
    }

    @SuppressWarnings("unchecked")
    @Test public void nestedNullOverrideCanClearInheritedAddonCapability() {
        ContentID parent=id("tractor"), child=id("tractor_icon");
        Map<String,Object> parentProperties=new LinkedHashMap<String,Object>();
        Map<String,Object> vehicle=new LinkedHashMap<String,Object>();
        vehicle.put("type","LAND");
        parentProperties.put("vehicle",vehicle);
        Map<String,Object> childProperties=new LinkedHashMap<String,Object>();
        childProperties.put("vehicle",null);
        ItemDefinitionRegistry result=compiler.compile(Arrays.asList(
                raw(parent,null,null,"minecraft:paper",null,null,null,null,parentProperties),
                raw(child,parent,null,null,null,null,null,null,childProperties)));
        assertTrue(result.get(child).get().properties().containsKey("vehicle"));
        assertEquals(null,result.get(child).get().properties().get("vehicle"));
        assertEquals("LAND",((Map<String,Object>)result.get(parent).get()
                .properties().get("vehicle")).get("type"));
    }

    @SuppressWarnings("unchecked")
    @Test public void deepMergesRenderRuleWhileChildModelWins() {
        ContentID parent=id("base_rule"), child=id("overridden_rule");
        Map<String,Object> baseModel=new LinkedHashMap<String,Object>();
        baseModel.put("type","minecraft:model");
        baseModel.put("id","voxelhorizons:vehicles/farming/tractor");
        baseModel.put("tint","color");
        Map<String,Object> baseRule=new LinkedHashMap<String,Object>();
        baseRule.put("model",baseModel);
        Map<String,Object> derivedModel=new LinkedHashMap<String,Object>();
        derivedModel.put("id","voxelhorizons:vehicles/farming/tractor_chest");
        Map<String,Object> derivedRule=new LinkedHashMap<String,Object>();
        derivedRule.put("model",derivedModel);
        RawItemDefinition base=raw(parent,null,null,"minecraft:paper",null,null,null,
                new RawItemRenderDefinition(null,null,null,null,null,baseRule),null);
        RawItemDefinition variant=raw(child,parent,null,null,null,null,null,
                new RawItemRenderDefinition(null,null,null,null,null,derivedRule),null);
        ItemDefinitionRegistry result=compiler.compile(Arrays.asList(base,variant));
        Map<String,Object> model=(Map<String,Object>)result.get(child).get().render()
                .rule().get("model");
        assertEquals("voxelhorizons:vehicles/farming/tractor_chest",model.get("id"));
        assertEquals("color",model.get("tint"));
        assertEquals("minecraft:model",model.get("type"));
        assertEquals("voxelhorizons:vehicles/farming/tractor",
                ((Map<String,Object>)result.get(parent).get().render().rule().get("model")).get("id"));
    }

    @Test public void mergesEventPathsAndReplacesOnlyMatchingActionLists() {
        ContentID parent=id("parent_events"), child=id("child_events");
        Map<String,java.util.List<ActionDefinition>> parentEntries=
                new LinkedHashMap<String,java.util.List<ActionDefinition>>();
        parentEntries.put("interact.right",Collections.singletonList(
                new ActionDefinition(ActionType.MESSAGE,Collections.<String,Object>singletonMap("text","base"))));
        parentEntries.put("interact.left",Collections.singletonList(
                new ActionDefinition(ActionType.CANCEL,Collections.<String,Object>emptyMap())));
        Map<String,java.util.List<ActionDefinition>> childEntries=
                new LinkedHashMap<String,java.util.List<ActionDefinition>>();
        childEntries.put("interact.right",Collections.singletonList(
                new ActionDefinition(ActionType.MESSAGE,Collections.<String,Object>singletonMap("text","child"))));
        childEntries.put("break",Collections.singletonList(
                new ActionDefinition(ActionType.CANCEL,Collections.<String,Object>emptyMap())));
        RawItemDefinition base=new RawItemDefinition(parent,null,null,"minecraft:paper",
                null,null,null,null,null,null,new EventActions(parentEntries));
        RawItemDefinition variant=new RawItemDefinition(child,parent,null,null,
                null,null,null,null,null,null,new EventActions(childEntries));
        ItemDefinitionRegistry compiled=compiler.compile(Arrays.asList(base,variant));
        EventActions events=compiled.get(child).get().events();
        assertEquals("child",events.forEvent("interact.right").get(0).string("text"));
        assertEquals(ActionType.CANCEL,events.forEvent("interact.left").get(0).type());
        assertEquals(ActionType.CANCEL,events.forEvent("break").get(0).type());
        assertEquals("base",compiled.get(parent).get().events()
                .forEvent("interact.right").get(0).string("text"));
    }

    @Test public void explicitlyEmptyEventMapDisablesInheritedEvents() {
        ContentID parent=id("parent_events_to_clear"), child=id("child_clears_events");
        Map<String,java.util.List<ActionDefinition>> parentEvents=
                new LinkedHashMap<String,java.util.List<ActionDefinition>>();
        parentEvents.put("interact.right",Collections.singletonList(
                new ActionDefinition(ActionType.CANCEL,Collections.<String,Object>emptyMap())));
        RawItemDefinition base=new RawItemDefinition(parent,null,null,"minecraft:paper",
                null,null,null,null,null,null,new EventActions(parentEvents));
        RawItemDefinition variant=new RawItemDefinition(child,parent,null,null,
                null,null,null,null,null,null,EventActions.empty());
        assertTrue(compiler.compile(Arrays.asList(base,variant)).get(child).get().events().isEmpty());
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
