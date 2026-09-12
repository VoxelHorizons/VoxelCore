package org.voxelhorizons.content.compile;

import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.item.ItemType;
import org.voxelhorizons.content.item.RawItemDefinition;
import org.voxelhorizons.content.item.RawItemRenderDefinition;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ItemDefinitionCompilerTest {

    private final ItemDefinitionCompiler compiler = new ItemDefinitionCompiler();

    @Test
    public void compilesPlainItemWithDefaults() {
        ContentID id = id("plain");
        RawItemDefinition raw = raw(id, null, null, "minecraft:paper", "Plain", null, null, null, null);

        ItemDefinition result = compiler.compile(Collections.singletonList(raw)).get(id).get();

        assertEquals(ItemType.ITEM, result.type());
        assertEquals("minecraft:paper", result.material());
        assertFalse(result.bound());
        assertTrue(result.lore().isEmpty());
    }

    @Test
    public void resolvesMultiLevelInheritance() {
        ContentID baseId = id("base");
        ContentID childId = id("child");
        ContentID grandchildId = id("grandchild");

        RawItemDefinition base = raw(baseId, null, ItemType.ITEM, "minecraft:paper", "Base",
                Arrays.asList("base lore"), Boolean.TRUE,
                new RawItemRenderDefinition("voxelhorizons:base", Integer.valueOf(10)), null);
        RawItemDefinition child = raw(childId, baseId, null, null, "Child", null, null,
                new RawItemRenderDefinition(null, Integer.valueOf(20)), null);
        RawItemDefinition grandchild = raw(grandchildId, childId, null, null, "Grandchild", null, null,
                new RawItemRenderDefinition("voxelhorizons:grandchild", null), null);

        ItemDefinition result = compiler.compile(Arrays.asList(base, child, grandchild)).get(grandchildId).get();

        assertEquals("minecraft:paper", result.material());
        assertEquals("Grandchild", result.displayName());
        assertEquals(Arrays.asList("base lore"), result.lore());
        assertTrue(result.bound());
        assertEquals("voxelhorizons:grandchild", result.render().model());
        assertEquals(Integer.valueOf(20), result.render().legacyCustomModelData());
    }

    @Test
    public void explicitFalseOverridesInheritedTrue() {
        ContentID baseId = id("base_bound");
        ContentID childId = id("child_bound");

        RawItemDefinition base = raw(baseId, null, null, "minecraft:paper", null, null, Boolean.TRUE, null, null);
        RawItemDefinition child = raw(childId, baseId, null, null, null, null, Boolean.FALSE, null, null);

        ItemDefinition result = compiler.compile(Arrays.asList(base, child)).get(childId).get();
        assertFalse(result.bound());
    }

    @Test
    public void childListReplacesParentList() {
        ContentID baseId = id("base_lore");
        ContentID childId = id("child_lore");

        RawItemDefinition base = raw(baseId, null, null, "minecraft:paper", null,
                Arrays.asList("one", "two"), null, null, null);
        RawItemDefinition child = raw(childId, baseId, null, null, null,
                Collections.singletonList("child"), null, null, null);

        assertEquals(Collections.singletonList("child"),
                compiler.compile(Arrays.asList(base, child)).get(childId).get().lore());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void deepMergesPropertyMaps() {
        ContentID baseId = id("base_props");
        ContentID childId = id("child_props");

        Map<String, Object> parentFurniture = new HashMap<String, Object>();
        parentFurniture.put("seats", Integer.valueOf(1));
        parentFurniture.put("storage", Boolean.FALSE);
        Map<String, Object> parentProperties = new HashMap<String, Object>();
        parentProperties.put("furniture", parentFurniture);

        Map<String, Object> childFurniture = new HashMap<String, Object>();
        childFurniture.put("storage", Boolean.TRUE);
        Map<String, Object> childProperties = new HashMap<String, Object>();
        childProperties.put("furniture", childFurniture);

        RawItemDefinition base = raw(baseId, null, null, "minecraft:paper", null, null, null, null, parentProperties);
        RawItemDefinition child = raw(childId, baseId, null, null, null, null, null, null, childProperties);

        Map<String, Object> furniture = (Map<String, Object>) compiler.compile(Arrays.asList(base, child))
                .get(childId).get().properties().get("furniture");

        assertEquals(Integer.valueOf(1), furniture.get("seats"));
        assertEquals(Boolean.TRUE, furniture.get("storage"));
    }

    @Test
    public void rejectsMissingParent() {
        RawItemDefinition child = raw(id("orphan"), id("missing"), null, null, null, null, null, null, null);
        expectCompileFailure(Collections.singletonList(child), "missing parent");
    }

    @Test
    public void rejectsSelfCycle() {
        ContentID id = id("self");
        RawItemDefinition item = raw(id, id, null, "minecraft:paper", null, null, null, null, null);
        expectCompileFailure(Collections.singletonList(item), "Circular item inheritance detected");
    }

    @Test
    public void rejectsMultiItemCycle() {
        ContentID a = id("cycle_a");
        ContentID b = id("cycle_b");
        ContentID c = id("cycle_c");

        expectCompileFailure(Arrays.asList(
                raw(a, b, null, "minecraft:paper", null, null, null, null, null),
                raw(b, c, null, null, null, null, null, null, null),
                raw(c, a, null, null, null, null, null, null, null)
        ), "cycle_a");
    }

    @Test
    public void rejectsDuplicateIds() {
        ContentID duplicate = id("duplicate");
        expectCompileFailure(Arrays.asList(
                raw(duplicate, null, null, "minecraft:paper", null, null, null, null, null),
                raw(duplicate, null, null, "minecraft:stone", null, null, null, null, null)
        ), "Duplicate item id");
    }

    @Test
    public void rejectsMissingMaterialAfterInheritance() {
        RawItemDefinition raw = raw(id("no_material"), null, null, null, null, null, null, null, null);
        expectCompileFailure(Collections.singletonList(raw), "has no material");
    }

    private void expectCompileFailure(java.util.Collection<RawItemDefinition> definitions, String expectedText) {
        try {
            compiler.compile(definitions);
            fail("Expected compilation to fail");
        } catch (ContentCompileException exception) {
            assertTrue("Expected message to contain '" + expectedText + "' but was: " + exception.getMessage(),
                    exception.getMessage().contains(expectedText));
        }
    }

    private ContentID id(String value) {
        return ContentID.of("voxelhorizons", value);
    }

    private RawItemDefinition raw(ContentID id,
                                  ContentID parent,
                                  ItemType type,
                                  String material,
                                  String displayName,
                                  java.util.List<String> lore,
                                  Boolean bound,
                                  RawItemRenderDefinition render,
                                  Map<String, Object> properties) {
        return new RawItemDefinition(id, parent, type, material, displayName, lore, bound, render, properties);
    }
}
