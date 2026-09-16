package org.voxelhorizons.content.block;

import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.compile.BlockDefinitionCompiler;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockAllocationRegistryTest {
    @Test
    public void keepsSlotsStableAndTombstoned() {
        ContentID first = ContentID.of("test", "first");
        ContentID second = ContentID.of("test", "second");
        BlockDefinitionCompiler compiler = new BlockDefinitionCompiler();
        BlockDefinitionRegistry initial = compiler.compile(Arrays.asList(block(first), block(second)));
        BlockAllocationRegistry allocated = BlockAllocationRegistry.reconcile(initial, null, true);
        assertEquals(BlockMethod.SOLID, allocated.get(first).get().method());
        assertEquals(0, allocated.get(first).get().slot());
        assertEquals(1, allocated.get(second).get().slot());

        BlockDefinitionRegistry onlySecond = compiler.compile(Collections.singletonList(block(second)));
        BlockAllocationRegistry reloaded = BlockAllocationRegistry.reconcile(onlySecond, allocated, true);
        assertFalse(reloaded.get(first).get().active());
        assertTrue(reloaded.get(second).get().active());
        assertEquals(1, reloaded.get(second).get().slot());
    }

    @Test
    public void autoUsesMushroomCarrierOnLegacyTargets() {
        ContentID id = ContentID.of("test", "legacy");
        BlockDefinitionRegistry definitions = new BlockDefinitionCompiler().compile(Collections.singletonList(block(id)));
        BlockAllocation allocation = BlockAllocationRegistry.reconcile(definitions, null, false).get(id).get();
        assertEquals(BlockMethod.MUSHROOM, allocation.method());
        assertTrue(BlockCarrierStates.state(allocation, false).startsWith("legacy:HUGE_MUSHROOM_1:"));
    }

    private static RawBlockDefinition block(ContentID id) {
        return new RawBlockDefinition(id, null, null, BlockMethod.AUTO, BlockModelPreset.CUBE_ALL,
                id.namespace() + ":block/" + id.value(), null, null, null, null, null, id, id, null);
    }
}
