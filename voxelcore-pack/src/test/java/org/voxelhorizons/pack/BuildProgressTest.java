package org.voxelhorizons.pack;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class BuildProgressTest {
    @Test public void reportsEachTenPercentOnceAndThenElapsedTime() {
        List<String> lines = new ArrayList<String>();
        BuildProgress.Step step = new BuildProgress.Step(lines::add, "Items", 200, false, 0L);
        for (int i = 0; i < 200; i++) step.advance(1, 1_000L);
        // 0% is not reported; 10..90% once each; 100% is left to finish().
        assertEquals(9, lines.size());
        assertEquals("Items: 10% (20 of 200)", lines.get(0));
        assertEquals("Items: 90% (180 of 200)", lines.get(8));
        step.finish("ok", 2_500_000_000L);
        assertEquals("Items: done in 2.5s (ok)", lines.get(9));
    }

    @Test public void unknownTotalReportsBytesOnlyEveryFiveSeconds() {
        List<String> lines = new ArrayList<String>();
        BuildProgress.Step step = new BuildProgress.Step(lines::add, "Download", -1, true, 0L);
        step.advance(1_048_576, 1_000_000_000L);
        assertTrue(lines.isEmpty());
        step.advance(1_048_576, 6_000_000_000L);
        assertEquals("Download: 2.0 MB so far", lines.get(0));
    }

    @Test public void largeDownloadShowsPercentInMegabytes() {
        List<String> lines = new ArrayList<String>();
        BuildProgress.Step step = new BuildProgress.Step(lines::add, "Jar", 10_485_760, true, 0L);
        step.advance(5_242_880, 1L);
        assertEquals("Jar: 50% (5.0 MB of 10.0 MB)", lines.get(0));
    }
}
