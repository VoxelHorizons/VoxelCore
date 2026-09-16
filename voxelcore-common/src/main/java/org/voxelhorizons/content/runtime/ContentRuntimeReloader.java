package org.voxelhorizons.content.runtime;

import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.load.ContentLoadException;
import org.voxelhorizons.content.load.ContentLoader;
import org.voxelhorizons.content.render.RenderAllocationRegistry;
import org.voxelhorizons.content.render.RenderAllocationStore;
import org.voxelhorizons.content.block.BlockAllocationRegistry;
import org.voxelhorizons.content.block.BlockAllocationStore;
import org.voxelhorizons.content.load.ContentDefinitions;

import java.nio.file.Path;
import java.util.Objects;

/** Builds replacement content in isolation and only publishes it after complete validation. */
public final class ContentRuntimeReloader {
    private final ContentLoader loader;
    private final Path contentRoot;
    private final ContentRuntime runtime;
    private final RenderAllocationStore allocationStore;
    private final ContentSnapshotValidator validator;
    private final BlockAllocationStore blockAllocationStore;
    private final boolean modernBlockStates;

    public ContentRuntimeReloader(ContentLoader loader, Path contentRoot, ContentRuntime runtime) {
        this(loader, contentRoot, runtime, null, null, null, true);
    }

    public ContentRuntimeReloader(ContentLoader loader, Path contentRoot, ContentRuntime runtime, RenderAllocationStore allocationStore) {
        this(loader, contentRoot, runtime, allocationStore, null, null, true);
    }

    public ContentRuntimeReloader(ContentLoader loader, Path contentRoot, ContentRuntime runtime,
                                  RenderAllocationStore allocationStore, ContentSnapshotValidator validator) {
        this(loader, contentRoot, runtime, allocationStore, validator, null, true);
    }

    public ContentRuntimeReloader(ContentLoader loader, Path contentRoot, ContentRuntime runtime,
                                  RenderAllocationStore allocationStore, ContentSnapshotValidator validator,
                                  BlockAllocationStore blockAllocationStore, boolean modernBlockStates) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.contentRoot = Objects.requireNonNull(contentRoot, "contentRoot");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.allocationStore = allocationStore;
        this.validator = validator;
        this.blockAllocationStore = blockAllocationStore;
        this.modernBlockStates = modernBlockStates;
    }

    public ContentReloadResult reload() {
        try {
            ContentDefinitions definitions = loader.loadDefinitions(contentRoot);
            ItemDefinitionRegistry nextRegistry = definitions.items();
            RenderAllocationRegistry nextAllocations = allocationStore == null
                    ? runtime.current().renderAllocations()
                    : RenderAllocationRegistry.reconcile(nextRegistry, runtime.current().renderAllocations());

            BlockAllocationRegistry nextBlockAllocations = blockAllocationStore == null
                    ? runtime.current().blockAllocations()
                    : BlockAllocationRegistry.reconcile(definitions.blocks(), runtime.current().blockAllocations(), modernBlockStates);

            if (validator != null) {
                validator.validate(nextRegistry, nextAllocations);
                validator.validateBlocks(definitions.blocks(), nextBlockAllocations);
            }
            if (allocationStore != null) allocationStore.save(nextAllocations);
            if (blockAllocationStore != null) blockAllocationStore.save(nextBlockAllocations);
            return ContentReloadResult.success(runtime.publish(nextRegistry, nextAllocations,
                    definitions.blocks(), nextBlockAllocations));
        } catch (ContentLoadException exception) {
            return ContentReloadResult.failure(runtime.current(), exception.getMessage());
        } catch (RuntimeException exception) {
            return ContentReloadResult.failure(runtime.current(), exception.getMessage());
        }
    }
}
