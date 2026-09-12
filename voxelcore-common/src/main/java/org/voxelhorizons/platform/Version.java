package org.voxelhorizons.platform;

public final class Version implements Comparable<Version> {
    private final int major;
    private final int minor;
    private final int patch;

    public Version(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public static Version of(int major, int minor, int patch) {
        return new Version(major, minor, patch);
    }

    public static Version parse(String version) {
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("Version cannot be null or empty.");
        }

        String[] parts = version.split("\\.");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return new Version(major, minor, patch);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid Minecraft version: " + version, exception);
        }
    }

    public boolean atLeast(int major, int minor, int patch) {
        return compareTo(new Version(major, minor, patch)) >= 0;
    }

    public boolean before(int major, int minor, int patch) {
        return compareTo(new Version(major, minor, patch)) < 0;
    }

    public int major() { return major; }
    public int minor() { return minor; }
    public int patch() { return patch; }

    @Override
    public int compareTo(Version other) {
        int result = Integer.compare(major, other.major);
        if (result != 0) return result;
        result = Integer.compare(minor, other.minor);
        if (result != 0) return result;
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
