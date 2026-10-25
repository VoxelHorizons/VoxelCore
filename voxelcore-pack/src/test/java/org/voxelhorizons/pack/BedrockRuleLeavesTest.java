package org.voxelhorizons.pack;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

public final class BedrockRuleLeavesTest {
    private static final BedrockRuleLeaves.IndexResolver INDICES = (type, key) -> {
        if (type == BedrockRuleLeaves.ValueType.STRING && key.equals("color")) return 0;
        if (type == BedrockRuleLeaves.ValueType.FLAG && key.equals("open")) return 1;
        if (type == BedrockRuleLeaves.ValueType.FLOAT && key.equals("fill")) return 2;
        return null;
    };

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) out.put((String) pairs[i], pairs[i + 1]);
        return out;
    }

    @Test public void selectCasesPrecedeFallbackWithDescendingPriority() throws Exception {
        Object rule = map("select", map("key", "color", "cases", map("red", "chair_red", "blue", "demo:chair_blue"),
                "fallback", "chair"));
        List<BedrockRuleLeaves.Leaf> leaves = BedrockRuleLeaves.flatten(rule, "demo", INDICES);
        assertEquals(3, leaves.size());
        assertEquals("demo:chair_blue", leaves.get(0).model);
        assertEquals("demo:chair_red", leaves.get(1).model);
        assertEquals("demo:chair", leaves.get(2).model);
        assertTrue(leaves.get(0).priority > leaves.get(1).priority && leaves.get(1).priority > leaves.get(2).priority);
        JsonObject match = leaves.get(1).predicates.get(0).getAsJsonObject();
        assertEquals("match", match.get("type").getAsString());
        assertEquals("custom_model_data", match.get("property").getAsString());
        assertEquals(0, match.get("index").getAsInt());
        assertEquals("red", match.get("value").getAsString());
        assertEquals(0, leaves.get(2).predicates.size());
    }

    @Test public void nestedConditionAndRangeCarryPathConjunctionAndJavaOrder() throws Exception {
        Object rule = map("condition", map("key", "open",
                "true", map("range", map("key", "fill", "entries", map("0.5", "half", "1", "full", "0", "empty"))),
                "false", map("model", map("id", "closed", "tint", "dye"))));
        List<BedrockRuleLeaves.Leaf> leaves = BedrockRuleLeaves.flatten(rule, "demo", INDICES);
        assertEquals(Arrays.asList("demo:full", "demo:half", "demo:empty", "demo:closed"),
                Arrays.asList(leaves.get(0).model, leaves.get(1).model, leaves.get(2).model, leaves.get(3).model));
        assertEquals(2, leaves.get(0).predicates.size());
        assertTrue(leaves.get(0).predicates.get(0).getAsJsonObject().get("expected").getAsBoolean());
        assertEquals("1", leaves.get(0).predicates.get(1).getAsJsonObject().get("threshold").getAsString());
        assertEquals("0.5", leaves.get(1).predicates.get(1).getAsJsonObject().get("threshold").getAsString());
        assertFalse(leaves.get(3).predicates.get(0).getAsJsonObject().get("expected").getAsBoolean());
        assertTrue(leaves.get(3).tinted);
        assertFalse(leaves.get(0).tinted);
    }

    @Test public void unallocatedKeysAndMalformedNodesAreRejected() {
        for (Object rule : new Object[]{
                map("select", map("key", "missing", "cases", map("a", "x"))),
                map("condition", map("key", "open", "true", "x")),
                map("range", map("key", "fill", "entries", map("NaN", "x"))),
                map("model", "x", "select", "y"),
                Integer.valueOf(3)}) {
            try { BedrockRuleLeaves.flatten(rule, "demo", INDICES); fail("Expected rejection: " + rule); }
            catch (IOException expected) { assertNotNull(expected.getMessage()); }
        }
    }
}
