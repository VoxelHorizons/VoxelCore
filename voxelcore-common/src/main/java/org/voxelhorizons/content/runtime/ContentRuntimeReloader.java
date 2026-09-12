package org.voxelhorizons.content.runtime;

import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.load.ContentLoadException;
import org.voxelhorizons.content.load.ContentLoader;
import org.voxelhorizons.content.render.RenderAllocationRegistry;
import org.voxelhorizons.content.render.RenderAllocationStore;

import java.nio.file.Path;
import java.util.Objects;

/** Builds replacement content in isolation and only publishes it after a successful load. */
public final class ContentRuntimeReloader {
    private final ContentLoader loader;
    private final Path contentRoot;
    private final ContentRuntime runtime;
    private final RenderAllocationStore allocationStore;

    public ContentRuntimeReloader(ContentLoader loader, Path contentRoot, ContentRuntime runtime) {
        this(loader, contentRoot, runtime, null);
    }

    public ContentRuntimeReloader(ContentLoader loader, Path contentRoot, ContentRuntime runtime, RenderAllocationStore allocationStore) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.contentRoot = Objects.requireNonNull(contentRoot, "contentRoot");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.allocationStore = allocationStore;
    }

    public ContentReloadResult reload() {
        try {
            ItemDefinitionRegistry nextRegistry = loader.load(contentRoot);
            if (allocationStore == null) {
                return ContentReloadResult.success(runtime.publish(nextRegistry));
            }
            RenderAllocationRegistry nextAllocations = RenderAllocationRegistry.reconcile(
                    nextRegistry, runtime.current().renderAllocations());
            allocationStore.save(nextAllocations);
            return ContentReloadResult.success(runtime.publish(nextRegistry, nextAllocations));
        } catch (ContentLoadException exception) {
            return ContentReloadResult.failure(runtime.current(), exception.getMessage());
        } catch (RuntimeException exception) {
            return ContentReloadResult.failure(runtime.current(), exception.getMessage());
        }
    }
}
