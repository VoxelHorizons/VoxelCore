package org.voxelhorizons.pack;

import java.nio.file.Path;

public final class JavaPackBuildResult {
    private final Path output;
    private final Path allocationManifest;
    private final int renderedItems;
    private final int copiedAssets;

    public JavaPackBuildResult(Path output, Path allocationManifest, int renderedItems, int copiedAssets) {
        this.output = output;
        this.allocationManifest = allocationManifest;
        this.renderedItems = renderedItems;
        this.copiedAssets = copiedAssets;
    }

    public Path output() { return output; }
    public Path allocationManifest() { return allocationManifest; }
    public int renderedItems() { return renderedItems; }
    public int copiedAssets() { return copiedAssets; }
}
