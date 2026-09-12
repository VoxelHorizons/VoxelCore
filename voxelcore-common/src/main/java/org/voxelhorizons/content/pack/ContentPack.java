package org.voxelhorizons.content.pack;

import java.nio.file.Path;

public final class ContentPack {
    private final Path root;
    private final ContentPackManifest manifest;

    public ContentPack(Path root, ContentPackManifest manifest) {
        this.root = root;
        this.manifest = manifest;
    }

    public Path root() {
        return root;
    }

    public ContentPackManifest manifest() {
        return manifest;
    }
}
