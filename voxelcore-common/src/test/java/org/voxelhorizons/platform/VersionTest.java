package org.voxelhorizons.platform;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class VersionTest {

    @Test
    public void parsesLegacyMinecraftVersion() {
        Version version = Version.parse("1.21.4");
        assertEquals(1, version.major());
        assertEquals(21, version.minor());
        assertEquals(4, version.patch());
    }

    @Test
    public void parsesPaperBuildQualifiedVersion() {
        Version version = Version.parse("26.2.build.123");
        assertEquals(26, version.major());
        assertEquals(2, version.minor());
        assertEquals(0, version.patch());
    }

    @Test
    public void parsesPaperStableVersionSuffix() {
        Version version = Version.parse("26.2.build.123-stable");
        assertEquals(26, version.major());
        assertEquals(2, version.minor());
        assertEquals(0, version.patch());
    }

    @Test
    public void rejectsNonNumericLeadingVersion() {
        try {
            Version.parse("build.123");
            fail("Expected invalid version");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
