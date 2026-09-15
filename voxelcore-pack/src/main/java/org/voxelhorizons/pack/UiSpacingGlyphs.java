package org.voxelhorizons.pack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared allocation used by both resource-pack compilation and runtime offset placeholders. */
public final class UiSpacingGlyphs {
    private static final int FIRST_CODE_POINT = 0xF800;
    private static final int MAX_OFFSET = 1024;
    private static final Map<Integer, Integer> CODE_POINTS_BY_DISTANCE;
    private static final Map<Integer, Integer> ADVANCES;

    static {
        Map<Integer, Integer> byDistance = new LinkedHashMap<Integer, Integer>();
        Map<Integer, Integer> advances = new LinkedHashMap<Integer, Integer>();
        int codePoint = FIRST_CODE_POINT;
        for (int distance = -MAX_OFFSET; distance <= MAX_OFFSET; distance = nextDistance(distance)) {
            if (distance == 0) continue;
            byDistance.put(Integer.valueOf(distance), Integer.valueOf(codePoint));
            advances.put(Integer.valueOf(codePoint), Integer.valueOf(distance));
            codePoint++;
        }
        CODE_POINTS_BY_DISTANCE = Collections.unmodifiableMap(byDistance);
        ADVANCES = Collections.unmodifiableMap(advances);
    }

    private UiSpacingGlyphs() {}

    public static Map<Integer, Integer> advances() { return ADVANCES; }
    public static int maxOffset() { return MAX_OFFSET; }

    /**
     * Returns one or more generated spacing characters whose advances sum to the requested offset.
     */
    public static String charactersForOffset(int offset) {
        if (offset == Integer.MIN_VALUE) throw new IllegalArgumentException("UI offset is too small");
        if (offset == 0) return "";
        StringBuilder result = new StringBuilder();
        int remaining = Math.abs(offset);
        int sign = offset < 0 ? -1 : 1;
        while (remaining >= MAX_OFFSET) {
            append(result, sign * MAX_OFFSET);
            remaining -= MAX_OFFSET;
        }
        for (int distance = MAX_OFFSET / 2; distance >= 1; distance /= 2) {
            if ((remaining & distance) != 0) append(result, sign * distance);
        }
        return result.toString();
    }

    private static void append(StringBuilder output, int distance) {
        Integer codePoint = CODE_POINTS_BY_DISTANCE.get(Integer.valueOf(distance));
        if (codePoint == null) throw new IllegalStateException("Missing spacing allocation for " + distance);
        output.appendCodePoint(codePoint.intValue());
    }

    private static int nextDistance(int value) {
        if (value == -1) return 0;
        if (value == 0) return 1;
        if (value < 0) return value / 2;
        return value * 2;
    }
}
