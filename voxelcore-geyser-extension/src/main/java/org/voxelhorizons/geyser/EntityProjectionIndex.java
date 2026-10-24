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
import java.util.Set;
import java.util.TreeSet;
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
        private final Integer dye;
        private Projection(String entityType,Float scale,Integer dye) {
            this.entityType=entityType;
            this.scale=scale;
            this.dye=dye;
        }
        public String entityType() { return entityType; }
        public Float scale() { return scale; }
        /** voxelcore:dye value: 0 = undyed, 1..16 = Java DyeColor order; null = not reported. */
        public Integer dye() { return dye; }
        /** Package-private so reconciler tests can build states without JSON. */
        static Projection of(String entityType,Float scale) { return new Projection(entityType,scale,null); }
        static Projection of(String entityType,Float scale,Integer dye) { return new Projection(entityType,scale,dye); }
        @Override public boolean equals(Object other) {
            return other instanceof Projection && entityType.equals(((Projection)other).entityType)
                    && Objects.equals(scale,((Projection)other).scale) && Objects.equals(dye,((Projection)other).dye);
        }
        @Override public int hashCode() { return Objects.hash(entityType,scale,dye); }
    }

    private final Path folder;
    private final Map<String,String> modelEntities;
    private Map<UUID,Projection> current = Collections.emptyMap();
    private Set<String> unmapped = Collections.emptySet();
    /** Feed file -> size/mtime/freshness seen at the last load; any change reloads all feeds. */
    private Map<Path,String> stamps=Collections.emptyMap();
    /** {@code furniture.json}, {@code vehicles.json} or a per-backend {@code furniture-<id>.json}. */
    static final java.util.regex.Pattern FEED=java.util.regex.Pattern.compile("(furniture|vehicles)(-[a-z0-9_-]{1,32})?\\.json");

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

    /** Addon model ids present in fresh Paper feeds with no registered Bedrock entity (B08 coverage). */
    public synchronized Set<String> unmappedModels() throws IOException {
        refresh();
        return unmapped;
    }

    public synchronized int size() throws IOException {
        refresh();
        return current.size();
    }

    private void refresh() throws IOException {
        Map<Path,String> next=new java.util.TreeMap<Path,String>();
        long now=System.currentTimeMillis();
        if(Files.isDirectory(folder,LinkOption.NOFOLLOW_LINKS)) {
            try(java.util.stream.Stream<Path> files=Files.list(folder)) {
                for(Path file:(Iterable<Path>)files::iterator) {
                    if(!FEED.matcher(file.getFileName().toString()).matches())continue;
                    long size=sizeOf(file),time=modified(file);
                    if(size<0)continue;
                    // Freshness can change even when size/mtime do not: expiring a feed must
                    // evict mappings already cached by an earlier spawn event.
                    boolean fresh=now-time<=MAX_SNAPSHOT_AGE_MILLIS;
                    next.put(file,size+"/"+time+"/"+fresh);
                }
            }
        }
        if(next.equals(stamps))return;
        Map<UUID,Projection> updated=new HashMap<UUID,Projection>();
        Set<String> missing=new TreeSet<String>();
        for(Map.Entry<Path,String> feed:next.entrySet()) {
            if(!feed.getValue().endsWith("/true"))continue;
            String name=feed.getKey().getFileName().toString();
            load(feed.getKey(),name.startsWith("furniture")?"furniture":"vehicles",updated,missing);
        }
        current=Collections.unmodifiableMap(updated);
        unmapped=Collections.unmodifiableSet(missing);
        stamps=next;
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

    private void load(Path file, String kind, Map<UUID,Projection> destination,
                      Set<String> missing) throws IOException {
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
                if(bedrockId==null){missing.add(model);continue;} // Never invent registrations.
                JsonObject links=row.getAsJsonObject("entity_links");
                if(links==null)continue;
                if("furniture".equals(kind)) {
                    add(links.get("display_entity_uuid"),bedrockId,scale(row.get("scale")),dye(row.get("dye_rgb")),destination);
                    JsonArray parts=links.getAsJsonArray("parts");
                    if(parts!=null)for(JsonElement partElement:parts) {
                        JsonObject part=partElement.getAsJsonObject();
                        if(!"ITEM".equals(part.get("type").getAsString()))continue;
                        JsonElement modelRef=part.get("model_item");
                        if(modelRef==null||modelRef.isJsonNull())continue;
                        String partType=modelEntities.get(modelRef.getAsString());
                        if(partType==null)missing.add(modelRef.getAsString());
                        else add(part.get("java_entity_uuid"),partType,
                                scale(part.get("scale")),null,destination);
                    }
                } else {
                    add(links.get("visual"),bedrockId,scale(row.get("scale")),dye(row.get("dye_rgb")),destination);
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

    /** Java DyeColor#getColor() RGB, DyeColor order; mirrors the pack's baked dye textures. */
    static final int[] JAVA_DYE_RGB = {0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA,
            0x474F52, 0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};

    /** Paper dyes with a single DyeColor, so matches are exact; other colours take the nearest. */
    static Integer dye(JsonElement value) {
        if(value==null||value.isJsonNull())return Integer.valueOf(0);
        int rgb=value.getAsInt()&0xFFFFFF, best=0;
        long bestDistance=Long.MAX_VALUE;
        for(int i=0;i<JAVA_DYE_RGB.length;i++) {
            int other=JAVA_DYE_RGB[i];
            long dr=((rgb>>16)&255)-((other>>16)&255), dg=((rgb>>8)&255)-((other>>8)&255), db=(rgb&255)-(other&255);
            long distance=dr*dr+dg*dg+db*db;
            if(distance<bestDistance){bestDistance=distance;best=i;}
        }
        return Integer.valueOf(best+1);
    }

    private static void add(JsonElement raw,String model,Float scale,Integer dye,
                            Map<UUID,Projection> result)throws IOException {
        if(raw==null||raw.isJsonNull())return;
        UUID id;
        try { id=UUID.fromString(raw.getAsString()); }
        catch(IllegalArgumentException invalid){throw new IOException("Invalid Paper display UUID in projection",invalid);}
        Projection projection=new Projection(model,scale,dye);
        Projection prior=result.putIfAbsent(id,projection);
        if(prior!=null && !prior.equals(projection))
            throw new IOException("Conflicting Paper entity UUID mapped to multiple visual states: "+id);
    }
}
