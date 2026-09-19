package org.voxelhorizons.command.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ContentBrowserTest {

    @Test
    public void reservesBottomRowForControls() {
        assertEquals(45, ContentBrowser.CONTENT_SLOTS);
        assertEquals(54, ContentBrowser.INVENTORY_SIZE);
        assertEquals(45, ContentBrowser.PREVIOUS_SLOT);
        assertEquals(49, ContentBrowser.CLOSE_SLOT);
        assertEquals(53, ContentBrowser.NEXT_SLOT);
    }

    @Test
    public void calculatesPagesFromFortyFiveContentSlots() {
        assertEquals(1, ContentBrowser.pageCount(0));
        assertEquals(1, ContentBrowser.pageCount(1));
        assertEquals(1, ContentBrowser.pageCount(45));
        assertEquals(2, ContentBrowser.pageCount(46));
        assertEquals(2, ContentBrowser.pageCount(90));
        assertEquals(3, ContentBrowser.pageCount(91));
    }
}
