package org.voxelhorizons.pack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolved container GUI font metrics including advance calculated from the current editable PNG size. */
public final class ContainerGuiLayout {
    private final ContainerGuiSettings settings;
    private final int singleAdvance;
    private final int doubleAdvance;

    private ContainerGuiLayout(ContainerGuiSettings settings, int singleAdvance, int doubleAdvance) {
        this.settings = settings;
        this.singleAdvance = singleAdvance;
        this.doubleAdvance = doubleAdvance;
    }

    public static ContainerGuiLayout defaults() {
        ContainerGuiSettings settings = ContainerGuiSettings.defaults();
        return new ContainerGuiLayout(settings,
                advance(176, 85, settings.singleScaleRatio()),
                advance(176, 139, settings.doubleScaleRatio()));
    }

    public static ContainerGuiLayout load(Path assetsRoot, ContainerGuiSettings settings) {
        if (settings == null) settings = ContainerGuiSettings.defaults();
        int[] single = dimensions(assetsRoot == null ? null : assetsRoot.resolve("generic_27_top.png"), 176, 85);
        int[] dbl = dimensions(assetsRoot == null ? null : assetsRoot.resolve("generic_54_top.png"), 176, 139);
        return new ContainerGuiLayout(settings,
                advance(single[0], single[1], settings.singleScaleRatio()),
                advance(dbl[0], dbl[1], settings.doubleScaleRatio()));
    }

    public ContainerGuiSettings settings() { return settings; }
    public int singleAdvance() { return singleAdvance; }
    public int doubleAdvance() { return doubleAdvance; }

    private static int advance(int width, int height, int scaleRatio) {
        return (int) Math.floor(((double) width * scaleRatio / height) + 0.5D) + 1;
    }

    private static int[] dimensions(Path file, int fallbackWidth, int fallbackHeight) {
        if (file == null || !Files.exists(file)) return new int[] {fallbackWidth, fallbackHeight};
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new JavaPackCompileException("Container GUI asset must be a regular PNG file: " + file);
        }
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null) throw new JavaPackCompileException("Container GUI asset is not a readable PNG: " + file);
            if (image.getWidth() < 1 || image.getHeight() < 1
                    || image.getWidth() > 4096 || image.getHeight() > 4096) {
                throw new JavaPackCompileException("Container GUI asset dimensions must be between 1 and 4096 pixels: " + file);
            }
            return new int[] {image.getWidth(), image.getHeight()};
        } catch (IOException exception) {
            throw new JavaPackCompileException("Unable to inspect container GUI asset " + file, exception);
        }
    }
}
