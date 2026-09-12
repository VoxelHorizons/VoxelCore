package org.voxelhorizons.pack;

import org.voxelhorizons.content.load.ContentLoader;
import org.voxelhorizons.content.pack.ContentPackDiscovery;

import java.nio.file.Path;

/** Deterministic Java resource-pack compiler for VoxelCore authored packs. */
public final class JavaPackCompiler {
    private final JavaPackCompilerEngine engine;

    public JavaPackCompiler() {
        this(new ContentPackDiscovery(), new ContentLoader());
    }

    JavaPackCompiler(ContentPackDiscovery packDiscovery, ContentLoader contentLoader) {
        this.engine = new JavaPackCompilerEngine(packDiscovery, contentLoader);
    }

    public JavaPackBuildResult compile(Path contentRoot, Path outputZip, Path allocationManifestPath, JavaPackTarget target) {
        return engine.compile(contentRoot, outputZip, allocationManifestPath, target, true);
    }

    /** Runs the complete pack validation/compiler pipeline without mutating the manifest or writing a ZIP. */
    public JavaPackBuildResult validate(Path contentRoot, Path allocationManifestPath, JavaPackTarget target) {
        return engine.compile(contentRoot, null, allocationManifestPath, target, false);
    }
}
