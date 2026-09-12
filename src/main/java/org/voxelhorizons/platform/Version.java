package org.voxelhorizons.platform;

public record Version(
        int major,
        int minor,
        int patch
) implements Comparable<Version> {

    public static Version of(
            int major,
            int minor,
            int patch
    ) {
        return new Version(major, minor, patch);
    }

    public boolean atLeast(
            int major,
            int minor,
            int patch
    ) {
        return compareTo(
                new Version(major, minor, patch)
        ) >= 0;
    }

    @Override
    public int compareTo(Version other) {
        int result = Integer.compare(major, other.major);

        if (result != 0) {
            return result;
        }

        result = Integer.compare(minor, other.minor);

        if (result != 0) {
            return result;
        }

        return Integer.compare(patch, other.patch);
    }
}