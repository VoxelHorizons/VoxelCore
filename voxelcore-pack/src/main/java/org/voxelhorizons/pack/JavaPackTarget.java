package org.voxelhorizons.pack;

public final class JavaPackTarget {
    private final String id;
    private final int packFormat;
    private final JavaPackMode mode;

    public JavaPackTarget(String id, int packFormat, JavaPackMode mode) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("target id cannot be empty");
        if (packFormat < 1) throw new IllegalArgumentException("pack format must be positive");
        if (mode == null) throw new IllegalArgumentException("pack mode cannot be null");
        this.id = id;
        this.packFormat = packFormat;
        this.mode = mode;
    }

    public String id() { return id; }
    public int packFormat() { return packFormat; }
    public JavaPackMode mode() { return mode; }

    public static JavaPackTarget legacyDamage(String id, int packFormat) {
        return new JavaPackTarget(id, packFormat, JavaPackMode.LEGACY_DAMAGE_UNBREAKABLE);
    }

    public static JavaPackTarget numericCmd(String id, int packFormat) {
        return new JavaPackTarget(id, packFormat, JavaPackMode.NUMERIC_CUSTOM_MODEL_DATA);
    }

    public static JavaPackTarget modern(String id, int packFormat) {
        return new JavaPackTarget(id, packFormat, JavaPackMode.ITEM_MODEL_1_21_4_PLUS);
    }
}
