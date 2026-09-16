package org.voxelhorizons.content.block;

import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.compile.BlockDefinitionCompiler;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BlockDefinitionCompilerTest {
    @Test
    public void resolvesInheritanceAndNormalizesColumnTextures() {
        ContentID baseId = ContentID.of("test", "base_log");
        ContentID childId = ContentID.of("test", "maple_log");
        Map<String, String> textures = new LinkedHashMap<String, String>();
        textures.put("side", "test:block/maple_log");
        textures.put("top", "test:block/maple_log_top");
        RawBlockDefinition base = new RawBlockDefinition(baseId, null, Boolean.TRUE, BlockMethod.AUTO,
                BlockModelPreset.CUBE_COLUMN, null, null, Double.valueOf(2.0D), null, null,
                Boolean.TRUE, null, null, null);
        RawBlockDefinition child = new RawBlockDefinition(childId, baseId, Boolean.FALSE, null,
                null, null, textures, null, null, null, null, childId, childId, null);

        BlockDefinition definition = new BlockDefinitionCompiler().compile(Arrays.asList(base, child)).get(childId).get();
        assertEquals(BlockMethod.AUTO, definition.method());
        assertEquals(BlockModelPreset.CUBE_COLUMN, definition.model());
        assertEquals("test:block/maple_log", definition.textures().get("side"));
        assertEquals("test:block/maple_log_top", definition.textures().get("top"));
        assertEquals("test:block/maple_log_top", definition.textures().get("bottom"));
        assertEquals(2.0D, definition.hardness(), 0.001D);
    }

    @Test
    public void expandsOneTextureToCubeAll() {
        ContentID id = ContentID.of("test", "ore");
        RawBlockDefinition raw = new RawBlockDefinition(id, null, null, null, null,
                "test:block/ore", null, null, null, null, null, id, id, null);
        BlockDefinition definition = new BlockDefinitionCompiler().compile(Collections.singletonList(raw)).get(id).get();
        assertEquals(Collections.singletonMap("all", "test:block/ore"), definition.textures());
        assertTrue(definition.dropWhenMined());
    }
}
