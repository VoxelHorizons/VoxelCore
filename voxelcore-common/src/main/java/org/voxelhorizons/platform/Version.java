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

        String normalized = version.trim();
        String[] parts = normalized.split("\\.");
        try {
            int major = numericPart(parts, 0, true);
            int minor = numericPart(parts, 1, false);
            int patch = numericPart(parts, 2, false);
            return new Version(major, minor, patch);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid Minecraft version: " + version, exception);
        }
    }

    private static int numericPart(String[] parts, int index, boolean required) {
        if (index >= parts.length) {
            if (required) throw new NumberFormatException("Missing version component " + index);
            return 0;
        }

        String part = parts[index];
        int length = 0;
        while (length < part.length() && Character.isDigit(part.charAt(length))) length++;
        if (length == 0) {
            if (required) throw new NumberFormatException("Version component is not numeric: " + part);
            return 0;
        }
        return Integer.parseInt(part.substring(0, length));
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
