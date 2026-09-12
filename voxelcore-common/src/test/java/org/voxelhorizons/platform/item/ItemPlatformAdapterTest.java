package org.voxelhorizons.platform.item;

import org.junit.Test;
import org.voxelhorizons.content.ContentID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ItemPlatformAdapterTest {
    @Test public void parsesValidStoredIdentity() {
        assertEquals(ContentID.of("test", "thing"), ItemPlatformAdapter.parseStoredContentId("test:thing").get());
    }

    @Test public void ignoresMissingOrCorruptStoredIdentity() {
        assertFalse(ItemPlatformAdapter.parseStoredContentId(null).isPresent());
        assertFalse(ItemPlatformAdapter.parseStoredContentId(" ").isPresent());
        assertFalse(ItemPlatformAdapter.parseStoredContentId("bad value with spaces").isPresent());
        assertTrue(ItemPlatformAdapter.parseStoredContentId("thing").isPresent());
    }
}
