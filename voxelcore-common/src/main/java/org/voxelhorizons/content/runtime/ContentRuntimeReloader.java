package org.voxelhorizons.content.runtime;

import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.load.ContentLoadException;
import org.voxelhorizons.content.load.ContentLoader;
import org.voxelhorizons.content.render.RenderAllocationRegistry;
import org.voxelhorizons.content.render.RenderAllocationStore;

import java.nio.file.Path;
import java.util.Objects;

/** Builds replacement content in isolation and only publishes it after complete validation. */
public final class ContentRuntimeReloader {
    private final ContentLoader loader;
    private final Path contentRoot;
    private final ContentRuntime runtime;
    private final RenderAllocationStore allocationStore;
    private final ContentSnapshotValidator validator;

    public ContentRuntimeReloader(ContentLoader loader, Path contentRoot, ContentRuntime runtime) {
        this(loader, contentRoot, runtime, null, null);
    }

    public ContentRuntimeReloader(ContentLoader loader, Path contentRoot, ContentRuntime runtime, RenderAllocationStore allocationStore) {
        this(loader, contentRoot, runtime, allocationStore, null);
    }

    public ContentRuntimeReloader(ContentLoader loader, Path contentRoot, ContentRuntime runtime,
                                  RenderAllocationStore allocationStore, ContentSnapshotValidator validator) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.contentRoot = Objects.requireNonNull(contentRoot, "contentRoot");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.allocationStore = allocationStore;
        this.validator = validator;
    }

    public ContentReloadResult reload() {
        try {
            ItemDefinitionRegistry nextRegistry = loader.load(contentRoot);
            RenderAllocationRegistry nextAllocations = allocationStore == null
                    ? runtime.current().renderAllocations()
                    : RenderAllocationRegistry.reconcile(nextRegistry, runtime.current().renderAllocations());

            if (validator != null) validator.validate(nextRegistry, nextAllocations);
            if (allocationStore != null) allocationStore.save(nextAllocations);
            return ContentReloadResult.success(runtime.publish(nextRegistry, nextAllocations));
        } catch (ContentLoadException exception) {
            return ContentReloadResult.failure(runtime.current(), exception.getMessage());
        } catch (RuntimeException exception) {
            return ContentReloadResult.failure(runtime.current(), exception.getMessage());
        }
    }
}
