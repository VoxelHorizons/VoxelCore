package org.voxelhorizons.geyser;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.entity.custom.CustomEntityDefinition;
import org.geysermc.geyser.api.entity.data.GeyserEntityDataTypes;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntityPropertiesEvent;
import org.geysermc.geyser.api.entity.property.type.GeyserIntEntityProperty;
import org.geysermc.geyser.api.entity.property.type.GeyserFloatEntityProperty;
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
import org.geysermc.geyser.api.event.lifecycle.GeyserPreInitializeEvent;
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
    /**
     * B17: a Geyser on a proxy/other host pulls the signed publication from Paper before
     * registries read packs and custom mappings. Configure {@code remote-sync.properties}
     * in this extension's data folder (url, secret); absent file = local/mounted mode.
     */
    @Subscribe
    public void onPreInitialize(GeyserPreInitializeEvent event) {
        java.nio.file.Path config = dataFolder().resolve("remote-sync.properties");
        if (!java.nio.file.Files.isRegularFile(config)) return;
        java.util.Properties properties = new java.util.Properties();
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(config, java.nio.charset.StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException unreadable) {
            logger().warning("VoxelCore remote sync config unreadable: " + unreadable.getMessage());
            return;
        }
        String url = properties.getProperty("url", "").trim();
        byte[] secret = properties.getProperty("secret", "").trim().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        configureRemoteFeeds(properties, secret);
        if (url.isEmpty()) return; // Projection-only configuration.
        try {
            java.net.URI base = java.net.URI.create(url);
            boolean loopback = "http".equalsIgnoreCase(base.getScheme()) && base.getHost() != null
                    && java.net.InetAddress.getByName(base.getHost()).isLoopbackAddress();
            if (!"https".equalsIgnoreCase(base.getScheme()) && !loopback
                    && !Boolean.parseBoolean(properties.getProperty("allow_insecure_http", "false")))
                throw new IOException("url must be https (or loopback http, or set allow_insecure_http=true on a private network)");
            RemotePublicationSync.Outcome outcome = RemotePublicationSync.sync(geyserApi().configDirectory(),
                    dataFolder().resolve("remote-sync-state.json"), secret,
                    RemotePublicationSync.http(base, RemotePublicationSync.pullToken(secret)), System.currentTimeMillis());
            logger().info("VoxelCore remote publication " + outcome + " from " + base.getHost() + ".");
        } catch (IOException | IllegalArgumentException failure) {
            // Fail closed: the previously installed verified pair (if any) stays in place.
            logger().warning("VoxelCore remote sync failed, keeping existing publication: " + failure.getMessage());
        }
    }

    /** {@code projection.<backend-id>.url=https://backend/bedrock} entries, one per Paper backend. */
    private void configureRemoteFeeds(java.util.Properties properties, byte[] secret) {
        for (String key : new TreeSet<String>(properties.stringPropertyNames())) {
            if (!key.startsWith("projection.") || !key.endsWith(".url")) continue;
            String backend = key.substring("projection.".length(), key.length() - ".url".length());
            try {
                java.net.URI base = java.net.URI.create(properties.getProperty(key).trim());
                if (!"https".equalsIgnoreCase(base.getScheme()) && !"http".equalsIgnoreCase(base.getScheme()))
                    throw new IllegalArgumentException("unsupported scheme");
                if ("http".equalsIgnoreCase(base.getScheme())
                        && !java.net.InetAddress.getByName(base.getHost()).isLoopbackAddress()
                        && !Boolean.parseBoolean(properties.getProperty("allow_insecure_http", "false")))
                    throw new IllegalArgumentException("http requires allow_insecure_http=true");
                remoteFeeds.add(new RemoteProjectionFeeds(geyserApi().configDirectory(), backend, secret,
                        RemoteProjectionFeeds.http(base, RemotePublicationSync.pullToken(secret))));
                logger().info("VoxelCore projection feed from backend '" + backend + "' at " + base.getHost());
            } catch (IOException | RuntimeException invalid) {
                logger().warning("VoxelCore projection feed '" + backend + "' not configured: " + invalid.getMessage());
            }
        }
    }

    @Subscribe
    public void onDefineResourcePacks(GeyserDefineResourcePacksEvent event) {
        report("resource pack registration phase");
        logger().info("Geyser currently has " + event.resourcePacks().size()
                + " registered packs; VoxelCore uses the normal packs/ scan.");
    }

    private volatile Map<String,String> nativeEntityIdentifiers = java.util.Collections.emptyMap();
    /** Bedrock entity identifier -> registered voxelcore:dye property (dyeable entities only). */
    private volatile Map<String,GeyserIntEntityProperty> dyeProperties = java.util.Collections.emptyMap();
    /** Pack transform-animation properties, in EntityProjectionIndex.Projection#transform order. */
    static final String[] TRANSFORM_PROPERTIES = {"voxelcore:tx", "voxelcore:ty", "voxelcore:tz",
            "voxelcore:pitch", "voxelcore:roll", "voxelcore:sx", "voxelcore:sy", "voxelcore:sz"};
    private static final float[] IDENTITY_TRANSFORM = {0, 0, 0, 0, 0, 1, 1, 1};
    private volatile Map<String,GeyserFloatEntityProperty[]> transformProperties = java.util.Collections.emptyMap();
    private volatile EntityProjectionIndex projectionIndex;
    private final ProjectionReconciler reconciler = new ProjectionReconciler();
    private volatile ScheduledExecutorService reconcileLoop;
    private final List<RemoteProjectionFeeds> remoteFeeds = new java.util.concurrent.CopyOnWriteArrayList<RemoteProjectionFeeds>();

    /** Matches the companions' 5-tick feed cadence (vehicle pitch/roll); unchanged feeds cost a directory stat. */
    static final long RECONCILE_PERIOD_MILLIS = 250L;
    /** Remote backends are polled over HTTP every fourth reconcile (once per second). */
    static final int REMOTE_POLL_EVERY = 4;
    private int reconcileTicks;

    /**
     * Keep server Paper UUID identity intact. Only known display entities from
     * explicitly exported VoxelFurniture/VoxelVehicles projection files are
     * replaced; ordinary Java displays and other entities remain unchanged.
     */
    @Subscribe
    public void onServerEntitySpawn(ServerSpawnEntityEvent event) {
        spawnStats.seen(String.valueOf(event.entityType()));
        EntityProjectionIndex current = projectionIndex;
        if (current==null) return;
        boolean itemDisplay=event.entityType().is(Identifier.of("minecraft:item_display"));
        // Diagnostics only: other model carriers a plugin might use. Plain mobs skip the feed lookup.
        Identifier carrier = itemDisplay ? null
                : OTHER_CARRIERS.stream().filter(type -> event.entityType().is(type)).findFirst().orElse(null);
        if (!itemDisplay && carrier == null) return;
        final EntityProjectionIndex.Projection projection;
        try {
            projection=current.projection(event.uuid());
        } catch (IOException readError) {
            logger().warning("VoxelCore projection failed: " + readError.getMessage());
            return;
        }
        if (!itemDisplay) {
            // A fed UUID on another entity type (armor stand, block display, ...) is never substituted.
            if (projection!=null) spawnStats.otherType(carrier.namespace() + ":" + carrier.path());
            return;
        }
        if (projection==null) { spawnStats.notInFeed.incrementAndGet(); return; }
        if (!nativeEntityIdentifiers.containsValue(projection.entityType())) {
            spawnStats.unregistered(projection.entityType());
            return;
        }
        try {
            event.definition(CustomEntityDefinition.of(Identifier.of(projection.entityType())));
            spawnStats.substituted.incrementAndGet();
            final Float visualScale=projection.scale();
            final GeyserIntEntityProperty dyeProperty=dyeProperties.get(projection.entityType());
            final Integer dye=projection.dye();
            final GeyserFloatEntityProperty[] transformProperty=transformProperties.get(projection.entityType());
            final float[] transform=projection.transform();
            if(visualScale!=null || (dyeProperty!=null && dye!=null) || (transformProperty!=null && transform!=null)) {
                event.preSpawnConsumer(entity -> {
                    if(visualScale!=null) entity.override(GeyserEntityDataTypes.SCALE,visualScale);
                    // Set before spawn so the AddEntity packet already carries texture and transform.
                    if(dyeProperty!=null && dye!=null) entity.updateProperty(dyeProperty,dye);
                    if(transformProperty!=null && transform!=null) applyTransform(entity,transformProperty,transform);
                });
            }
        } catch (RuntimeException invalid) {
            spawnStats.rejected.incrementAndGet();
            logger().warning("VoxelCore custom entity replacement rejected for "
                    + event.uuid() + ": " + invalid.getMessage());
        }
    }

    private final SpawnStats spawnStats = new SpawnStats();
    private static final List<Identifier> OTHER_CARRIERS = List.of(Identifier.of("minecraft:armor_stand"),
            Identifier.of("minecraft:block_display"), Identifier.of("minecraft:interaction"));


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
        long now = System.currentTimeMillis();
        if (reconcileTicks++ % REMOTE_POLL_EVERY == 0)
            for (RemoteProjectionFeeds feeds : remoteFeeds) feeds.poll(now);
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

    private List<ProjectionReconciler.Target> liveTargets(List<? extends GeyserConnection> connections, UUID uuid) {
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
                @Override public void dye(int dye) {
                    GeyserIntEntityProperty property = dyeProperties.get(definitionId());
                    if (property != null) entity.updatePropertiesBatched(update -> update.update(property, dye), true);
                }
                @Override public void transform(float[] transform) {
                    GeyserFloatEntityProperty[] properties = transformProperties.get(definitionId());
                    if (properties != null) applyTransform(entity, properties, transform == null ? IDENTITY_TRANSFORM : transform);
                }
            });
        }
        return targets;
    }

    private static void applyTransform(GeyserEntity entity, GeyserFloatEntityProperty[] properties, float[] values) {
        entity.updatePropertiesBatched(update -> {
            for (int i = 0; i < properties.length; i++) update.update(properties[i], Float.valueOf(values[i]));
        }, true);
    }

    @Subscribe
    public synchronized void onShutdown(GeyserShutdownEvent event) {
        if (reconcileLoop != null) {
            reconcileLoop.shutdownNow();
            reconcileLoop = null;
        }
    }

    /**
     * Fired by Geyser right after GeyserDefineEntitiesEvent. Registers the int property
     * voxelcore:dye (0 = undyed, 1..16 = Java dye colours) that the pack's shared dye render
     * controller reads with query.property, for entities the pack declares dyeable.
     */
    @Subscribe
    public void onDefineEntityProperties(GeyserDefineEntityPropertiesEvent event) {
        if (nativeEntityIdentifiers.isEmpty()) return;
        java.util.Set<String> dyeable;
        try {
            dyeable = EntityDefinitionCatalog.loadDyeable(geyserApi().configDirectory());
        } catch (IOException invalid) {
            logger().warning("VoxelCore dye properties not registered: " + invalid.getMessage());
            dyeable = java.util.Collections.emptySet();
        }
        Map<String,GeyserFloatEntityProperty[]> transforms = new java.util.HashMap<String,GeyserFloatEntityProperty[]>();
        for (String identifier : new TreeSet<String>(nativeEntityIdentifiers.values())) {
            try {
                GeyserFloatEntityProperty[] properties = new GeyserFloatEntityProperty[TRANSFORM_PROPERTIES.length];
                for (int i = 0; i < properties.length; i++) {
                    float limit = i < 3 ? 64f : i < 5 ? 180f : 64f;
                    properties[i] = event.registerFloatProperty(Identifier.of(identifier), Identifier.of(TRANSFORM_PROPERTIES[i]),
                            i < 5 ? -limit : 0f, limit, Float.valueOf(IDENTITY_TRANSFORM[i]));
                }
                transforms.put(identifier, properties);
            } catch (RuntimeException incompatible) {
                logger().warning("Could not register transform properties for " + identifier + ": " + incompatible.getMessage());
            }
        }
        transformProperties = java.util.Collections.unmodifiableMap(transforms);
        Map<String,GeyserIntEntityProperty> registered = new java.util.HashMap<String,GeyserIntEntityProperty>();
        for (String identifier : dyeable) {
            if (!nativeEntityIdentifiers.containsValue(identifier)) continue;
            try {
                registered.put(identifier, event.registerIntegerProperty(Identifier.of(identifier),
                        Identifier.of("voxelcore:dye"), 0, 16, 0));
            } catch (RuntimeException incompatible) {
                logger().warning("Could not register dye property for " + identifier + ": " + incompatible.getMessage());
            }
        }
        dyeProperties = java.util.Collections.unmodifiableMap(registered);
        if (!registered.isEmpty()) logger().info("Registered voxelcore:dye on " + registered.size() + " dyeable entities.");
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
                    EntityProjectionIndex index = projectionIndex;
                    if (index != null) {
                        try {
                            Set<String> missing = index.unmappedModels();
                            source.sendMessage("Projected entities: " + index.size() + "; addon models without a Bedrock entity: "
                                    + missing.size() + (missing.isEmpty() ? "" : " " + preview(missing)));
                            source.sendMessage("Spawns since start: " + spawnStats.describe());
                        } catch (IOException invalid) {
                            source.sendMessage("Projection feed invalid: " + invalid.getMessage());
                        }
                    }
                })
                .build());
    }

    private static String preview(Set<String> models) {
        StringBuilder text = new StringBuilder("[");
        int shown = 0;
        for (String model : models) {
            if (shown++ == 8) { text.append(", ..."); break; }
            if (shown > 1) text.append(", ");
            text.append(model);
        }
        return text.append(']').toString();
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
