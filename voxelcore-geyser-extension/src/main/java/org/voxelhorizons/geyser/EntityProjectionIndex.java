package org.voxelhorizons.geyser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative Paper-to-Geyser read-only entity projection.
 *
 * The writer is the corresponding Paper plugin and replaces the file atomically.
 * This reader never invents entity ids and uses only known VoxelCore model mappings.
 * It does not mutate any seats, hitboxes, inventories or ownership.
 */
public final class EntityProjectionIndex {
    /** Publishers renew unchanged snapshots at least every 10 seconds. After 60 seconds
     * without a replacement, an orphaned Paper feed must never replace a new Java entity. */
    static final long MAX_SNAPSHOT_AGE_MILLIS = 60_000L;

    /** Supported Bedrock spawn-time model state. Null scale means leave Geyser default. */
    public static final class Projection {
        private final String entityType;
        private final Float scale;
        private Projection(String entityType,Float scale) {
            this.entityType=entityType;
            this.scale=scale;
        }
        public String entityType() { return entityType; }
        public Float scale() { return scale; }
        /** Package-private so reconciler tests can build states without JSON. */
        static Projection of(String entityType,Float scale) { return new Projection(entityType,scale); }
        @Override public boolean equals(Object other) {
            return other instanceof Projection && entityType.equals(((Projection)other).entityType)
                    && Objects.equals(scale,((Projection)other).scale);
        }
        @Override public int hashCode() { return Objects.hash(entityType,scale); }
    }

    private final Path folder;
    private final Map<String,String> modelEntities;
    private Map<UUID,Projection> current = Collections.emptyMap();
    private long furnitureSize=-1,vehiclesSize=-1;
    private long furnitureTime=-1,vehiclesTime=-1;
    private boolean furnitureFresh,vehiclesFresh;

    public EntityProjectionIndex(Path geyserData, Map<String,String> modelEntities) {
        if (geyserData==null || modelEntities==null)
            throw new IllegalArgumentException("Projection inputs required");
        this.folder=geyserData.resolve("voxelcore-projection");
        this.modelEntities=Collections.unmodifiableMap(new HashMap<String,String>(modelEntities));
    }

    public synchronized String entityType(UUID paperEntityUuid) throws IOException {
        Projection projection=projection(paperEntityUuid);
        return projection==null ? null : projection.entityType();
    }

    public synchronized Projection projection(UUID paperEntityUuid) throws IOException {
        refresh();
        return current.get(paperEntityUuid);
    }

    /** Immutable view of all currently authoritative projections, refreshed from disk. */
    public synchronized Map<UUID,Projection> snapshot() throws IOException {
        refresh();
        return current;
    }

    public synchronized int size() throws IOException {
        refresh();
        return current.size();
    }

    private void refresh() throws IOException {
        Path furniture=folder.resolve("furniture.json"), vehicles=folder.resolve("vehicles.json");
        long fSize=sizeOf(furniture),vSize=sizeOf(vehicles);
        long fTime=modified(furniture),vTime=modified(vehicles);
        long now=System.currentTimeMillis();
        boolean fFresh=fSize>=0 && now-fTime<=MAX_SNAPSHOT_AGE_MILLIS;
        boolean vFresh=vSize>=0 && now-vTime<=MAX_SNAPSHOT_AGE_MILLIS;
        // Freshness can change even when size/mtime do not: expiring a feed must
        // evict mappings already cached by an earlier spawn event.
        if(fSize==furnitureSize && vSize==vehiclesSize && fTime==furnitureTime && vTime==vehiclesTime
                && fFresh==furnitureFresh && vFresh==vehiclesFresh)
            return;
        Map<UUID,Projection> updated=new HashMap<UUID,Projection>();
        if(fFresh)load(furniture,"furniture",updated);
        if(vFresh)load(vehicles,"vehicles",updated);
        current=Collections.unmodifiableMap(updated);
        furnitureSize=fSize;vehiclesSize=vSize;
        furnitureTime=fTime;vehiclesTime=vTime;
        furnitureFresh=fFresh;vehiclesFresh=vFresh;
    }

    private static long sizeOf(Path path) throws IOException {
        if(Files.isSymbolicLink(path))
            throw new IOException("Unsafe projection symlink "+path);
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ? Files.size(path):-1;
    }
    private static long modified(Path path) throws IOException {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                ? Files.getLastModifiedTime(path,LinkOption.NOFOLLOW_LINKS).toMillis():-1;
    }

    private void load(Path file, String kind, Map<UUID,Projection> destination) throws IOException {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))return;
        JsonObject root;
        try {
            if(Files.size(file)>5_000_000)throw new IOException("Projection state exceeds 5MB: "+file);
            root=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("schema").getAsInt()!=1)
                throw new IOException("Unsupported Paper projection schema in "+file);
            JsonArray rows=root.getAsJsonArray(kind);
            if(rows==null)throw new IOException("Missing projection array "+kind);
            for(JsonElement item:rows) {
                JsonObject row=item.getAsJsonObject();
                String model=row.get("model_item").getAsString();
                String bedrockId=modelEntities.get(model);
                if(bedrockId==null)continue; // Never invent registrations.
                JsonObject links=row.getAsJsonObject("entity_links");
                if(links==null)continue;
                if("furniture".equals(kind)) {
                    add(links.get("display_entity_uuid"),bedrockId,scale(row.get("scale")),destination);
                    JsonArray parts=links.getAsJsonArray("parts");
                    if(parts!=null)for(JsonElement partElement:parts) {
                        JsonObject part=partElement.getAsJsonObject();
                        if(!"ITEM".equals(part.get("type").getAsString()))continue;
                        JsonElement modelRef=part.get("model_item");
                        if(modelRef==null||modelRef.isJsonNull())continue;
                        String partType=modelEntities.get(modelRef.getAsString());
                        if(partType!=null)add(part.get("java_entity_uuid"),partType,
                                scale(part.get("scale")),destination);
                    }
                } else {
                    add(links.get("visual"),bedrockId,scale(row.get("scale")),destination);
                    // A chest accessory uses a distinct model; do not overwrite
                    // it with the root vehicle geometry by guessing its identity.
                }
            }
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid Paper projection state in "+file,invalid);
        }
    }

    /** Geyser's SCALE is a uniform Float. Never turn a non-uniform XYZ
     * transform into a guessed uniform scale or trust invalid numeric values. */
    private static Float scale(JsonElement value) {
        if(value==null||value.isJsonNull())return null;
        float amount;
        if(value.isJsonArray()) {
            JsonArray values=value.getAsJsonArray();
            if(values.size()!=3)return null;
            amount=values.get(0).getAsFloat();
            if(Math.abs(amount-values.get(1).getAsFloat())>0.0001f
                    ||Math.abs(amount-values.get(2).getAsFloat())>0.0001f)return null;
        } else {
            amount=value.getAsFloat();
        }
        return Float.isFinite(amount) && amount>0f && amount<=64f
                ? Float.valueOf(amount) : null;
    }

    private static void add(JsonElement raw,String model,Float scale,
                            Map<UUID,Projection> result)throws IOException {
        if(raw==null||raw.isJsonNull())return;
        UUID id;
        try { id=UUID.fromString(raw.getAsString()); }
        catch(IllegalArgumentException invalid){throw new IOException("Invalid Paper display UUID in projection",invalid);}
        Projection projection=new Projection(model,scale);
        Projection prior=result.putIfAbsent(id,projection);
        if(prior!=null && (!prior.entityType().equals(model) || !Objects.equals(prior.scale(),scale)))
            throw new IOException("Conflicting Paper entity UUID mapped to multiple visual states: "+id);
    }
}
