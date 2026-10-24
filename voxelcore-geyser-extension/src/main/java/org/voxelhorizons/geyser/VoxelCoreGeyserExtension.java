package org.voxelhorizons.geyser;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.entity.custom.CustomEntityDefinition;
import org.geysermc.geyser.api.entity.data.GeyserEntityDataTypes;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent;
import org.geysermc.geyser.api.event.java.ServerSpawnEntityEvent;
import org.geysermc.geyser.api.util.Identifier;
import java.util.Map;
import java.util.TreeSet;
import java.io.IOException;
import java.util.Set;
import org.geysermc.geyser.api.command.Command;
import org.geysermc.geyser.api.command.CommandSource;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCommandsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserShutdownEvent;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.entity.type.GeyserEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.geysermc.geyser.api.extension.Extension;

/**
 * Geyser-side companion: audits the local VoxelCore pack publication *before*
 * players are admitted. It does not register the pack a second time because
 * Geyser already scans its packs folder. Nor does a matching on-disk receipt
 * prove that a running Geyser loaded updated mappings without a restart.
 */
public final class VoxelCoreGeyserExtension implements Extension {
    @Subscribe
    public void onDefineResourcePacks(GeyserDefineResourcePacksEvent event) {
        report("resource pack registration phase");
        logger().info("Geyser currently has " + event.resourcePacks().size()
                + " registered packs; VoxelCore uses the normal packs/ scan.");
    }

    private volatile Map<String,String> nativeEntityIdentifiers = java.util.Collections.emptyMap();
    private volatile EntityProjectionIndex projectionIndex;
    private final ProjectionReconciler reconciler = new ProjectionReconciler();
    private volatile ScheduledExecutorService reconcileLoop;

    /** Projection files are polled once per second; unchanged files cost two stat calls. */
    static final long RECONCILE_PERIOD_MILLIS = 1_000L;

    /**
     * Keep server Paper UUID identity intact. Only known display entities from
     * explicitly exported VoxelFurniture/VoxelVehicles projection files are
     * replaced; ordinary Java displays and other entities remain unchanged.
     */
    @Subscribe
    public void onServerEntitySpawn(ServerSpawnEntityEvent event) {
        EntityProjectionIndex current = projectionIndex;
        if (current==null || !event.entityType().is(Identifier.of("minecraft:item_display"))) return;
        final EntityProjectionIndex.Projection projection;
        try {
            projection=current.projection(event.uuid());
        } catch (IOException readError) {
            logger().warning("VoxelCore projection failed: " + readError.getMessage());
            return;
        }
        if (projection==null || !nativeEntityIdentifiers.containsValue(projection.entityType())) return;
        try {
            event.definition(CustomEntityDefinition.of(Identifier.of(projection.entityType())));
            final Float visualScale=projection.scale();
            if(visualScale!=null) {
                event.preSpawnConsumer(entity -> entity.override(GeyserEntityDataTypes.SCALE,visualScale));
            }
        } catch (RuntimeException invalid) {
            logger().warning("VoxelCore custom entity replacement rejected for "
                    + event.uuid() + ": " + invalid.getMessage());
        }
    }


    @Subscribe
    public void onDefineEntities(GeyserDefineEntitiesEvent event) {
        PublicationVerifier.State state = PublicationVerifier.inspect(geyserApi().configDirectory());
        if (state.status()!=PublicationVerifier.Status.VERIFIED_ON_DISK) {
            logger().warning("VoxelCore native entity types not registered: publication is " + state.status());
            return;
        }
        Map<String,String> catalog;
        try {
            catalog=EntityDefinitionCatalog.load(geyserApi().configDirectory());
        } catch (IOException invalid) {
            logger().warning("VoxelCore native entities not registered: " + invalid.getMessage());
            return;
        }
        Set<String> types = new TreeSet<String>(catalog.values());
        for(String identifier:types) {
            try {
                event.register(CustomEntityDefinition.of(Identifier.of(identifier)));
            } catch (RuntimeException incompatible) {
                logger().warning("Could not register VoxelCore entity " + identifier + ": " + incompatible.getMessage());
                // A partial registration must not enable an incorrect model lookup.
                nativeEntityIdentifiers=java.util.Collections.emptyMap();
                return;
            }
        }
        nativeEntityIdentifiers = catalog;
        projectionIndex = new EntityProjectionIndex(geyserApi().configDirectory(), catalog);
        startReconcileLoop();
        logger().info("Registered " + types.size() + " VoxelCore Bedrock client entity definitions from "
                + state.revision() + " (known UUID spawn replacement, uniform scale and live scale reconcile active).");
    }

    private synchronized void startReconcileLoop() {
        if (reconcileLoop != null) return;
        reconcileLoop = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "VoxelCore-Geyser-projection");
            thread.setDaemon(true);
            return thread;
        });
        reconcileLoop.scheduleWithFixedDelay(this::reconcileProjections,
                RECONCILE_PERIOD_MILLIS, RECONCILE_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
    }

    /** Apply changed Paper projection state to entities already visible to Bedrock sessions. */
    private void reconcileProjections() {
        EntityProjectionIndex current = projectionIndex;
        if (current == null) return;
        try {
            List<? extends GeyserConnection> connections = geyserApi().onlineConnections();
            ProjectionReconciler.Result result = reconciler.reconcile(current.snapshot(),
                    uuid -> liveTargets(connections, uuid));
            if (result.needsRespawn > 0) {
                logger().warning("VoxelCore projection changed model for " + result.needsRespawn
                        + " spawned Bedrock entities; Geyser cannot redefine them until Paper re-sends the entity.");
            }
        } catch (IOException readError) {
            logger().warning("VoxelCore projection reconcile skipped: " + readError.getMessage());
        } catch (RuntimeException unexpected) {
            // Never let one bad tick cancel the scheduled loop.
            logger().warning("VoxelCore projection reconcile failed: " + unexpected);
        }
    }

    private static List<ProjectionReconciler.Target> liveTargets(List<? extends GeyserConnection> connections, UUID uuid) {
        List<ProjectionReconciler.Target> targets = new ArrayList<ProjectionReconciler.Target>();
        for (GeyserConnection connection : connections) {
            GeyserEntity entity = connection.entities().byUuid(uuid);
            if (entity == null) continue;
            targets.add(new ProjectionReconciler.Target() {
                @Override public String definitionId() {
                    Identifier id = entity.definition().identifier();
                    return id.namespace() + ":" + id.path();
                }
                @Override public void scale(Float scale) {
                    entity.override(GeyserEntityDataTypes.SCALE, scale);
                }
            });
        }
        return targets;
    }

    @Subscribe
    public synchronized void onShutdown(GeyserShutdownEvent event) {
        if (reconcileLoop != null) {
            reconcileLoop.shutdownNow();
            reconcileLoop = null;
        }
    }

    @Subscribe
    public void onDefineCommands(GeyserDefineCommandsEvent event) {
        event.register(Command.builder(this)
                .name("status")
                .description("Inspect VoxelCore's Geyser pack and mapping revision on disk")
                .source(CommandSource.class)
                .playerOnly(false)
                .suggestedOpOnly(true)
                .permission("voxelcore.geyser.status")
                .executor((source, command, args) -> {
                    PublicationVerifier.State state = PublicationVerifier.inspect(geyserApi().configDirectory());
                    source.sendMessage("VoxelCore Bedrock: " + state.status()
                            + (state.revision().isEmpty() ? "" : " revision=" + state.revision())
                            + " (" + state.details() + ")");
                    source.sendMessage("Note: this verifies on-disk artifacts, not each client's active resources.");
                })
                .build());
    }

    @Subscribe
    public void onPostInitialize(GeyserPostInitializeEvent event) {
        report("post-initialize");
    }

    private void report(String phase) {
        PublicationVerifier.State state = PublicationVerifier.inspect(geyserApi().configDirectory());
        if (state.status() == PublicationVerifier.Status.VERIFIED_ON_DISK) {
            logger().info("VoxelCore " + phase + " deployment " + state.revision()
                    + " verified on disk. This is NOT proof that live Bedrock sessions have loaded it.");
        } else if (state.status() == PublicationVerifier.Status.NOT_PUBLISHED) {
            logger().warning("No VoxelCore Bedrock resource pack deployment found in "
                    + geyserApi().configDirectory() + ". Build/publish from Paper first.");
        } else {
            logger().warning("VoxelCore " + phase + " publication " + state.status()
                    + ": " + state.details() + ". Do not accept mismatched pack/mappings.");
        }
    }
}
