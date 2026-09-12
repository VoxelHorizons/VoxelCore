package org.voxelhorizons.content.runtime;

import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.load.ContentLoadException;
import org.voxelhorizons.content.load.ContentLoader;

import java.nio.file.Path;
import java.util.Objects;

/** Builds replacement content in isolation and only publishes it after a successful load. */
public final class ContentRuntimeReloader {
    private final ContentLoader loader;
    private final Path contentRoot;
    private final ContentRuntime runtime;

    public ContentRuntimeReloader(ContentLoader loader, Path contentRoot, ContentRuntime runtime) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.contentRoot = Objects.requireNonNull(contentRoot, "contentRoot");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public ContentReloadResult reload() {
        try {
            ItemDefinitionRegistry nextRegistry = loader.load(contentRoot);
            return ContentReloadResult.success(runtime.publish(nextRegistry));
        } catch (ContentLoadException exception) {
            return ContentReloadResult.failure(runtime.current(), exception.getMessage());
        }
    }
}
