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
        assertEquals(BlockMethod.MUSHROOM, allocated.get(first).get().method());
        assertEquals(BlockMethod.MUSHROOM, allocated.get(second).get().method());
        assertEquals(11, allocated.get(first).get().slot()); // north + south = 12, slot = bits-1
        assertEquals(12, allocated.get(second).get().slot()); // north + south + down

        BlockDefinitionRegistry onlySecond = compiler.compile(Collections.singletonList(block(second)));
        BlockAllocationRegistry reloaded = BlockAllocationRegistry.reconcile(onlySecond, allocated, true);
        assertFalse(reloaded.get(first).get().active());
        assertTrue(reloaded.get(second).get().active());
        assertEquals(12, reloaded.get(second).get().slot());
    }

    @Test
    public void autoUsesMushroomCarrierOnLegacyTargets() {
        ContentID id = ContentID.of("test", "legacy");
        BlockDefinitionRegistry definitions = new BlockDefinitionCompiler().compile(Collections.singletonList(block(id)));
        BlockAllocation allocation = BlockAllocationRegistry.reconcile(definitions, null, false).get(id).get();
        assertEquals(BlockMethod.MUSHROOM, allocation.method());
        assertTrue(BlockCarrierStates.state(allocation, false).startsWith("legacy:HUGE_MUSHROOM_1:"));
    }

    @Test
    public void conservativelyReservedMushroomsExcludeDefaultAndUseNoNotes() {
        assertFalse(BlockCarrierStates.safeForNewAllocation(BlockMethod.SOLID, 0, true));
        assertFalse(BlockCarrierStates.reservedMushroomSlot(62)); // all faces true is ordinary placement
        assertFalse(BlockCarrierStates.reservedMushroomSlot(0));
        assertTrue(BlockCarrierStates.reservedMushroomSlot(11));
        assertTrue(BlockCarrierStates.reservedMushroomSlot(33));
        for (int slot = 0; slot < BlockCarrierStates.capacity(BlockMethod.MUSHROOM, true); slot++)
            if (BlockCarrierStates.safeForNewAllocation(BlockMethod.MUSHROOM, slot, true))
                assertTrue(BlockCarrierStates.state(new BlockAllocation(BlockMethod.MUSHROOM, slot, true), true)
                        .startsWith("minecraft:"));
    }

    @Test
    public void existingNoteBlockSlotsRemainStableAfterPolicyChange() {
        ContentID id = ContentID.of("test", "old_note");
        java.util.Map<ContentID, BlockAllocation> previous = new java.util.LinkedHashMap<ContentID, BlockAllocation>();
        previous.put(id, new BlockAllocation(BlockMethod.SOLID, 0, true));
        BlockDefinitionRegistry definitions = new BlockDefinitionCompiler().compile(Collections.singletonList(block(id)));
        BlockAllocation actual = BlockAllocationRegistry.reconcile(
                definitions, new BlockAllocationRegistry(previous), true).get(id).get();
        assertEquals(BlockMethod.SOLID, actual.method());
        assertEquals(0, actual.slot());
    }

    private static RawBlockDefinition block(ContentID id) {
        return new RawBlockDefinition(id, null, null, BlockMethod.AUTO, BlockModelPreset.CUBE_ALL,
                id.namespace() + ":block/" + id.value(), null, null, null, null, null, id, id, null);
    }
}
