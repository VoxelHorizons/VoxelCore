package org.voxelhorizons.content.item;

import org.junit.Test;
import org.voxelhorizons.content.ContentID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ItemDefinitionImmutabilityTest {

    @SuppressWarnings("unchecked")
    @Test
    public void deeplyCopiesAndFreezesProperties() {
        List<Object> sourceList = new ArrayList<Object>();
        sourceList.add("original");
        Map<String, Object> nested = new LinkedHashMap<String, Object>();
        nested.put("values", sourceList);
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("nested", nested);

        ItemDefinition definition = new ItemDefinition(ContentID.of("test", "item"), ItemType.ITEM,
                "minecraft:paper", null, Collections.<String>emptyList(), false,
                Optional.<ContentID>empty(), null, properties);

        sourceList.add("mutated-after-construction");
        nested.put("other", Boolean.TRUE);

        Map<String, Object> frozenNested = (Map<String, Object>) definition.properties().get("nested");
        List<Object> frozenList = (List<Object>) frozenNested.get("values");
        assertEquals(Collections.singletonList("original"), frozenList);

        try { frozenNested.put("x", "y"); fail("Expected nested properties map to be immutable"); }
        catch (UnsupportedOperationException expected) { }
        try { frozenList.add("x"); fail("Expected nested properties list to be immutable"); }
        catch (UnsupportedOperationException expected) { }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void deeplyCopiesAndFreezesRenderRules() {
        Map<String, Object> branch = new LinkedHashMap<String, Object>();
        branch.put("fallback", "test:item/base");
        Map<String, Object> select = new LinkedHashMap<String, Object>();
        select.put("key", "variant");
        select.put("cases", branch);
        Map<String, Object> rule = new LinkedHashMap<String, Object>();
        rule.put("select", select);

        ItemRenderDefinition render = new ItemRenderDefinition("test:item/base", null, null,
                Collections.<String, Boolean>emptyMap(), null, rule);
        branch.put("mutated", Arrays.<Object>asList("bad"));

        Map<String, Object> frozenSelect = (Map<String, Object>) render.rule().get("select");
        Map<String, Object> frozenCases = (Map<String, Object>) frozenSelect.get("cases");
        assertEquals(1, frozenCases.size());
        try { frozenCases.put("x", "y"); fail("Expected nested render rule map to be immutable"); }
        catch (UnsupportedOperationException expected) { }
    }
}
