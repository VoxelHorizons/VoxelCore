package org.voxelhorizons.command.commands;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class GiveCompletionsTest {
    @Test public void filtersNamespacedIdsIgnoringCaseAndSortsThem() {
        assertEquals(Arrays.asList("voxel:amber", "voxel:ruby"), GiveCompletions.matching(
                Arrays.asList("other:ruby", "voxel:ruby", "voxel:amber"), "VOXEL:"));
    }

    @Test public void completesAmountsWithoutLoadingPlayerList() {
        assertEquals(Arrays.asList("1", "16"), GiveCompletions.complete(
                new String[] {"voxel:ruby", "1"}, Collections.<String>emptyList()));
        assertEquals(Collections.emptyList(), GiveCompletions.complete(
                new String[] {"voxel:ruby", "64", "someone", "extra"}, Collections.<String>emptyList()));
    }
}
