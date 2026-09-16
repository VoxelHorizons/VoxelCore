package org.voxelhorizons.pack;

import java.nio.file.Path;

public final class JavaPackBuildResult {
    private final Path output;
    private final Path allocationManifest;
    private final int renderedItems;
    private final int copiedAssets;
    private final int renderedBlocks;

    public JavaPackBuildResult(Path output, Path allocationManifest, int renderedItems, int copiedAssets) {
        this(output, allocationManifest, renderedItems, copiedAssets, 0);
    }

    public JavaPackBuildResult(Path output, Path allocationManifest, int renderedItems, int copiedAssets, int renderedBlocks) {
        this.output = output;
        this.allocationManifest = allocationManifest;
        this.renderedItems = renderedItems;
        this.copiedAssets = copiedAssets;
        this.renderedBlocks = renderedBlocks;
    }

    public Path output() { return output; }
    public Path allocationManifest() { return allocationManifest; }
    public int renderedItems() { return renderedItems; }
    public int copiedAssets() { return copiedAssets; }
    public int renderedBlocks() { return renderedBlocks; }
}
