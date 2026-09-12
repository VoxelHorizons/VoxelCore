package org.voxelhorizons.pack;

import org.voxelhorizons.platform.Version;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class JavaPackTarget {
    public static final JavaPackTarget MC_1_12_2 = legacyDamage("mc-1.12.2", 3);
    public static final JavaPackTarget MC_1_13_2 = legacyDamage("mc-1.13.2", 4);
    public static final JavaPackTarget MC_1_14_4 = numericCmd("mc-1.14.4", 4);
    public static final JavaPackTarget MC_1_19_4 = numericCmd("mc-1.19.4", 13);
    public static final JavaPackTarget MC_1_20_5 = numericCmd("mc-1.20.5", 32);
    public static final JavaPackTarget MC_1_21_4 = modern("mc-1.21.4", 46);
    public static final JavaPackTarget MC_26_2 = modernRange("mc-26.2", 88, 0);

    private static final List<JavaPackTarget> KNOWN = Collections.unmodifiableList(Arrays.asList(
            MC_1_12_2, MC_1_13_2, MC_1_14_4, MC_1_19_4, MC_1_20_5, MC_1_21_4, MC_26_2));

    private final String id;
    private final int packFormat;
    private final int packFormatMinor;
    private final boolean rangeMetadata;
    private final JavaPackMode mode;

    public JavaPackTarget(String id, int packFormat, JavaPackMode mode) {
        this(id, packFormat, 0, false, mode);
    }

    private JavaPackTarget(String id, int packFormat, int packFormatMinor, boolean rangeMetadata, JavaPackMode mode) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("target id cannot be empty");
        if (packFormat < 1) throw new IllegalArgumentException("pack format must be positive");
        if (packFormatMinor < 0) throw new IllegalArgumentException("pack format minor must be non-negative");
        if (mode == null) throw new IllegalArgumentException("pack mode cannot be null");
        this.id = id;
        this.packFormat = packFormat;
        this.packFormatMinor = packFormatMinor;
        this.rangeMetadata = rangeMetadata;
        this.mode = mode;
    }

    public String id() { return id; }
    public int packFormat() { return packFormat; }
    public int packFormatMinor() { return packFormatMinor; }
    public boolean usesRangeMetadata() { return rangeMetadata; }
    public JavaPackMode mode() { return mode; }

    public static JavaPackTarget legacyDamage(String id, int packFormat) { return new JavaPackTarget(id, packFormat, JavaPackMode.LEGACY_DAMAGE_UNBREAKABLE); }
    public static JavaPackTarget numericCmd(String id, int packFormat) { return new JavaPackTarget(id, packFormat, JavaPackMode.NUMERIC_CUSTOM_MODEL_DATA); }
    public static JavaPackTarget modern(String id, int packFormat) { return new JavaPackTarget(id, packFormat, JavaPackMode.ITEM_MODEL_1_21_4_PLUS); }
    public static JavaPackTarget modernRange(String id, int packFormat, int packFormatMinor) {
        return new JavaPackTarget(id, packFormat, packFormatMinor, true, JavaPackMode.ITEM_MODEL_1_21_4_PLUS);
    }
    public static List<JavaPackTarget> knownTargets() { return KNOWN; }

    public static JavaPackTarget byId(String id) {
        if (id == null) return null;
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (JavaPackTarget target : KNOWN) if (target.id.equals(normalized)) return target;
        return null;
    }

    /** Returns a target only for an exact version profile VoxelCore explicitly validates. */
    public static JavaPackTarget forVersion(Version version) {
        if (version == null) return null;
        if (version.compareTo(Version.of(1, 12, 2)) == 0) return MC_1_12_2;
        if (version.compareTo(Version.of(1, 13, 2)) == 0) return MC_1_13_2;
        if (version.compareTo(Version.of(1, 14, 4)) == 0) return MC_1_14_4;
        if (version.compareTo(Version.of(1, 19, 4)) == 0) return MC_1_19_4;
        if (version.compareTo(Version.of(1, 20, 5)) == 0) return MC_1_20_5;
        if (version.compareTo(Version.of(1, 21, 4)) == 0) return MC_1_21_4;
        if (version.compareTo(Version.of(26, 2, 0)) == 0) return MC_26_2;
        return null;
    }

    @Override public String toString() { return id; }
}
