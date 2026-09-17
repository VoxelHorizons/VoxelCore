package org.voxelhorizons.text;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerListPlaceholderSynchronizerTest {
    @Test
    public void onlyWritesNonNullChangedValues() {
        assertTrue(PlayerListPlaceholderSynchronizer.changed(":owner:", "\uE001"));
        assertFalse(PlayerListPlaceholderSynchronizer.changed("\uE001", "\uE001"));
        assertFalse(PlayerListPlaceholderSynchronizer.changed(null, null));
        assertFalse(PlayerListPlaceholderSynchronizer.changed("header", null));
    }
}
