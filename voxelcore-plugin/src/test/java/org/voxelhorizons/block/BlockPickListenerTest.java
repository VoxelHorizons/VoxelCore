package org.voxelhorizons.block;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockPickListenerTest {

    @Test
    public void acceptsOnlyRegisteredEventType() {
        assertTrue(BlockPickListener.acceptsEvent(PickBlockEvent.class, new PickBlockEvent()));
        assertFalse(BlockPickListener.acceptsEvent(PickBlockEvent.class, new PickEntityEvent()));
    }

    @Test
    public void rejectsNulls() {
        assertFalse(BlockPickListener.acceptsEvent(null, new PickBlockEvent()));
        assertFalse(BlockPickListener.acceptsEvent(PickBlockEvent.class, null));
    }

    private static final class PickBlockEvent {}

    private static final class PickEntityEvent {}
}
