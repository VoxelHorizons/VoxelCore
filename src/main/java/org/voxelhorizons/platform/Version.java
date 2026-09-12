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

    public static Version parse(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException(
                    "Version cannot be null or empty."
            );
        }

        String[] parts = version.split("\\.");

        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1
                    ? Integer.parseInt(parts[1])
                    : 0;

            int patch = parts.length > 2
                    ? Integer.parseInt(parts[2])
                    : 0;

            return new Version(
                    major,
                    minor,
                    patch
            );

        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Invalid Minecraft version: " + version,
                    exception
            );
        }
    }

    public boolean atLeast(
            int major,
            int minor,
            int patch
    ) {
        return compareTo(
                new Version(
                        major,
                        minor,
                        patch
                )
        ) >= 0;
    }

    @Override
    public int compareTo(Version other) {
        int result =
                Integer.compare(
                        major,
                        other.major
                );

        if (result != 0) {
            return result;
        }

        result =
                Integer.compare(
                        minor,
                        other.minor
                );

        if (result != 0) {
            return result;
        }

        return Integer.compare(
                patch,
                other.patch
        );
    }
}