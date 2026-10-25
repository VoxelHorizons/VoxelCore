package org.voxelhorizons.geyser;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Validates model->custom Bedrock entity identifiers against the exact installed
 * resource pack. A corrupt or missing model/texture means the type must not be
 * registered with Geyser; Paper gameplay remains authoritative.
 */
public final class EntityDefinitionCatalog {
    private EntityDefinitionCatalog() {}

    public static Map<String,String> load(Path geyserRoot) throws IOException {
        Path path = geyserRoot.resolve("packs/voxelcore.mcpack");
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path))
            throw new IOException("VoxelCore Bedrock pack absent/unsafe at " + path);
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry index = zip.getEntry("voxelcore/entity_index.json");
            if (index == null) return Collections.emptyMap(); // pre-entity compiler pack
            JsonObject file = JsonParser.parseString(read(zip,index)).getAsJsonObject();
            if (!file.has("schema") || file.get("schema").getAsInt()!=1)
                throw new IOException("Unsupported VoxelCore entity-index schema");
            JsonObject models = file.getAsJsonObject("content_models");
            if (models == null) throw new IOException("Missing content_models in Bedrock entity index");
            Map<String,String> result = new TreeMap<String,String>();
            Set<String> names = new TreeSet<String>();
            for (Map.Entry<String, JsonElement> mapping : models.entrySet()) {
                String contentId = mapping.getKey(), identifier = mapping.getValue().getAsString();
                if (!contentId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                    throw new IOException("Unsafe content model identifier: " + contentId);
                if (!identifier.matches("voxelcore:entity_i_[0-9a-f]{32}"))
                    throw new IOException("Unsafe generated Bedrock entity identifier: " + identifier);
                String suffix=identifier.substring("voxelcore:entity_".length());
                String entityPath="entity/voxelcore/"+suffix+".entity.json";
                JsonObject description=resource(zip,entityPath,"minecraft:client_entity");
                if (!identifier.equals(description.get("identifier").getAsString()))
                    throw new IOException("Client entity identifier differs from index for " + contentId);
                String geometry=description.getAsJsonObject("geometry").get("default").getAsString();
                if (!geometry.equals("geometry.voxelcore."+suffix))
                    throw new IOException("Unexpected geometry identifier for "+contentId);
                String texture=description.getAsJsonObject("textures").get("default").getAsString();
                if (!texture.equals("textures/voxelcore/g_"+suffix))
                    throw new IOException("Unexpected texture for "+contentId);
                if (zip.getEntry(texture+".png")==null)
                    throw new IOException("Missing Bedrock entity texture "+texture);
                String geomPath="models/entity/voxelcore/"+suffix+".geo.json";
                JsonObject geo=resource(zip,geomPath,null);
                JsonObject actual=geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                        .getAsJsonObject("description");
                if (!geometry.equals(actual.get("identifier").getAsString()))
                    throw new IOException("Geometry identifier differs from entity for "+contentId);
                result.put(contentId,identifier);
                names.add(identifier);
            }
            return Collections.unmodifiableMap(result);
        } catch (RuntimeException malformed) {
            throw new IOException("Invalid VoxelCore client entity index/JSON", malformed);
        }
    }

    /**
     * Entities whose pack declares the dye texture array (entity_index "dyeable_entities");
     * only these get the voxelcore:dye property. Unknown/unsafe entries are rejected.
     */
    public static Set<String> loadDyeable(Path geyserRoot) throws IOException {
        Path path = geyserRoot.resolve("packs/voxelcore.mcpack");
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) return Collections.emptySet();
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry index = zip.getEntry("voxelcore/entity_index.json");
            if (index == null) return Collections.emptySet();
            JsonObject file = JsonParser.parseString(read(zip,index)).getAsJsonObject();
            if (!file.has("dyeable_entities")) return Collections.emptySet();
            Set<String> out = new TreeSet<String>();
            for (JsonElement entry : file.getAsJsonArray("dyeable_entities")) {
                String identifier = entry.getAsString();
                if (!identifier.matches("voxelcore:entity_i_[0-9a-f]{32}"))
                    throw new IOException("Unsafe dyeable entity identifier: " + identifier);
                JsonObject textures = resource(zip,"entity/voxelcore/"+identifier.substring("voxelcore:entity_".length())
                        +".entity.json","minecraft:client_entity").getAsJsonObject("textures");
                if (!textures.has("dye_16")) throw new IOException("Dyeable entity lacks dye textures: " + identifier);
                out.add(identifier);
            }
            return Collections.unmodifiableSet(out);
        } catch (RuntimeException malformed) {
            throw new IOException("Invalid VoxelCore dyeable entity list", malformed);
        }
    }

    private static JsonObject resource(ZipFile zip,String path,String member) throws IOException {
        ZipEntry entry=zip.getEntry(path);
        if (entry==null)throw new IOException("Missing Bedrock entity resource "+path);
        JsonObject object=JsonParser.parseString(read(zip,entry)).getAsJsonObject();
        return member==null?object:object.getAsJsonObject(member).getAsJsonObject("description");
    }
    private static String read(ZipFile zip,ZipEntry entry)throws IOException{
        try(InputStream input=zip.getInputStream(entry)){
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
}
