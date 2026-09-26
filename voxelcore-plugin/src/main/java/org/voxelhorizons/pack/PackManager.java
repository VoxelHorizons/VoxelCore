package org.voxelhorizons.pack;

import org.voxelhorizons.platform.Version;

import java.nio.file.Path;

/** Owns live Java resource-pack validation/build lifecycle for the plugin data directory. */
public final class PackManager {
    private final JavaPackCompiler compiler;
    private final Path contentRoot;
    private final Path allocationManifest;
    private final Path outputRoot;
    private final Path glyphAllocationManifest;
    private final Path tooltipAssetsRoot;
    private final Path containerGuiAssetsRoot;
    private final ContainerGuiSettings containerGuiSettings;
    private final Version serverVersion;

    public PackManager(Path dataRoot, Path contentRoot, Version serverVersion) {
        this(dataRoot, contentRoot, serverVersion, ContainerGuiSettings.defaults());
    }

    public PackManager(Path dataRoot, Path contentRoot, Version serverVersion,
                       ContainerGuiSettings containerGuiSettings) {
        if (dataRoot == null || contentRoot == null || serverVersion == null || containerGuiSettings == null) {
            throw new IllegalArgumentException("Pack manager arguments cannot be null");
        }
        this.compiler = new JavaPackCompiler();
        this.contentRoot = contentRoot;
        this.allocationManifest = dataRoot.resolve("render-allocations.yml");
        this.outputRoot = dataRoot.resolve("build").resolve("resource-packs");
        this.glyphAllocationManifest = dataRoot.resolve("glyph-allocations.yml");
        this.tooltipAssetsRoot = dataRoot.resolve("assets").resolve("tooltip");
        this.containerGuiAssetsRoot = dataRoot.resolve("assets").resolve("container").resolve("gui");
        this.containerGuiSettings = containerGuiSettings;
        this.serverVersion = serverVersion;
    }

    public JavaPackTarget currentTarget() { return JavaPackTarget.forVersion(serverVersion); }
    public Path contentRoot() { return contentRoot; }
    public Path allocationManifest() { return allocationManifest; }
    public Path outputRoot() { return outputRoot; }
    public Path glyphAllocationManifest() { return glyphAllocationManifest; }
    public ContainerGuiLayout containerGuiLayout() {
        return ContainerGuiLayout.load(containerGuiAssetsRoot, containerGuiSettings);
    }
    public UiGlyphRegistry uiGlyphs(boolean persist) {
        return compiler.loadUiGlyphs(contentRoot, glyphAllocationManifest, persist);
    }

    public JavaPackBuildResult validate(JavaPackTarget target) {
        if (target == null) throw new IllegalArgumentException("Pack target cannot be null");
        return compiler.validate(contentRoot, allocationManifest, target, tooltipAssetsRoot,
                containerGuiAssetsRoot, containerGuiSettings);
    }

    public JavaPackBuildResult build(JavaPackTarget target) {
        if (target == null) throw new IllegalArgumentException("Pack target cannot be null");
        Path output = outputRoot.resolve(target.id() + ".zip");
        return compiler.compile(contentRoot, output, allocationManifest, target, tooltipAssetsRoot,
                containerGuiAssetsRoot, containerGuiSettings);
    }
}
