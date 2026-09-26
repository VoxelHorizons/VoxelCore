package org.voxelhorizons.pack;

/** Configurable font metrics for the built-in single/double chest restoration glyphs. */
public final class ContainerGuiSettings {
    public static final int DEFAULT_SINGLE_SCALE_RATIO = 85;
    public static final int DEFAULT_SINGLE_Y_POSITION = 13;
    public static final int DEFAULT_DOUBLE_SCALE_RATIO = 139;
    public static final int DEFAULT_DOUBLE_Y_POSITION = 13;

    private final int singleScaleRatio;
    private final int singleYPosition;
    private final int doubleScaleRatio;
    private final int doubleYPosition;

    public ContainerGuiSettings(int singleScaleRatio, int singleYPosition,
                                int doubleScaleRatio, int doubleYPosition) {
        validate("single", singleScaleRatio, singleYPosition);
        validate("double", doubleScaleRatio, doubleYPosition);
        this.singleScaleRatio = singleScaleRatio;
        this.singleYPosition = singleYPosition;
        this.doubleScaleRatio = doubleScaleRatio;
        this.doubleYPosition = doubleYPosition;
    }

    public static ContainerGuiSettings defaults() {
        return new ContainerGuiSettings(
                DEFAULT_SINGLE_SCALE_RATIO, DEFAULT_SINGLE_Y_POSITION,
                DEFAULT_DOUBLE_SCALE_RATIO, DEFAULT_DOUBLE_Y_POSITION);
    }

    public int singleScaleRatio() { return singleScaleRatio; }
    public int singleYPosition() { return singleYPosition; }
    public int doubleScaleRatio() { return doubleScaleRatio; }
    public int doubleYPosition() { return doubleYPosition; }

    private static void validate(String name, int scaleRatio, int yPosition) {
        if (scaleRatio < 1 || scaleRatio > 1024) {
            throw new IllegalArgumentException("ui.chest_prefixes." + name
                    + ".scale_ratio must be between 1 and 1024");
        }
        if (yPosition > scaleRatio) {
            throw new IllegalArgumentException("ui.chest_prefixes." + name
                    + ".y_position must be lower than or equal to scale_ratio");
        }
    }
}
