package org.voxelhorizons.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.voxelhorizons.content.block.BlockAllocation;
import org.voxelhorizons.content.block.BlockAllocationRegistry;
import org.voxelhorizons.content.block.BlockAllocationStore;
import org.voxelhorizons.content.block.BlockCarrierStates;
import org.voxelhorizons.content.block.BlockDefinition;
import org.voxelhorizons.content.block.BlockModelPreset;
import org.voxelhorizons.content.load.ContentLoader;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Maps existing Paper-owned Java carrier states to native Geyser custom blocks.
 * This only supplies client-side appearance. Placing, mining, drops, and persistence
 * continue to be owned by the original Bukkit block and VoxelCore BlockManager.
 */
final class BedrockBlockCompiler {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

    static final class Output {
        final byte[] mapping;
        final int count;
        Output(byte[] mapping, int count) { this.mapping = mapping; this.count = count; }
    }

    private BedrockBlockCompiler() {}

    static Output append(Path contentRoot, Path allocationManifest,
                         Map<String, byte[]> javaAssets, Map<String, byte[]> bedrockAssets,
                         List<String> unsupported) throws IOException {
        JsonObject mappings = new JsonObject();
        mappings.addProperty("format_version", 1);
        JsonObject blocks = new JsonObject();
        mappings.add("blocks", blocks);

        java.util.List<BlockDefinition> definitions = new java.util.ArrayList<BlockDefinition>(
                new ContentLoader().loadDefinitions(contentRoot).blocks().entries().values());
        definitions.removeIf(BlockDefinition::abstractDefinition);
        definitions.sort((a, b) -> a.id().toString().compareTo(b.id().toString()));
        if (definitions.isEmpty()) return new Output(bytes(mappings), 0);
        if (allocationManifest == null || !Files.isRegularFile(allocationManifest))
            throw new IOException("Bedrock block mappings require the current block-allocations.yml; build the Java pack first");

        BlockAllocationRegistry allocations;
        try {
            allocations = new BlockAllocationStore(allocationManifest).load();
        } catch (RuntimeException invalid) {
            throw new IOException("Unable to load block allocations for Bedrock: " + invalid.getMessage(), invalid);
        }

        JsonObject terrain = terrain(bedrockAssets.get("textures/terrain_texture.json"));
        JsonObject textureData = terrain.getAsJsonObject("texture_data");
        int count = 0;
        for (BlockDefinition block : definitions) {
            BlockAllocation allocation = allocations.get(block.id()).orElse(null);
            if (allocation == null || !allocation.active()) {
                unsupported.add("block " + block.id() + ": no active block allocation; rebuild Java pack first");
                continue;
            }
            String state;
            try { state = BlockCarrierStates.state(allocation, true); }
            catch (RuntimeException invalid) {
                unsupported.add("block " + block.id() + ": unsupported modern carrier: " + invalid.getMessage());
                continue;
            }
            if (state.startsWith("legacy:")) {
                unsupported.add("block " + block.id() + ": legacy mushroom metadata has no Bedrock mapping");
                continue;
            }
            // Assemble all resources first. A single missing/corrupt face must not emit an
            // incomplete Geyser mapping or accidentally modify a vanilla blockstate.
            Map<String, byte[]> generatedTextures = new TreeMap<String, byte[]>();
            JsonObject generatedTerrain = new JsonObject();
            JsonObject materials = new JsonObject();
            String shortId = hash(block.id().toString()).substring(0, 24);
            boolean singleTexture = true;
            String firstTexture = faceTexture(block, FACES[0]);
            for (String face : FACES)
                if (!firstTexture.equals(faceTexture(block, face))) singleTexture = false;
            String[] exposed = singleTexture ? new String[]{"*"} : FACES;
            String missing = null;
            for (String face : exposed) {
                String texture = faceTexture(block, "*".equals(face) ? FACES[0] : face);
                int colon = texture.indexOf(':');
                if (colon < 1) { missing = "invalid texture " + texture; break; }
                String source = "assets/" + texture.substring(0, colon) + "/textures/"
                        + texture.substring(colon + 1) + ".png";
                byte[] png = javaAssets.get(source);
                if (png == null) { missing = "missing Java block texture " + source; break; }
                try {
                    if (ImageIO.read(new ByteArrayInputStream(png)) == null)
                        throw new IOException("not a supported PNG image");
                } catch (IOException invalid) {
                    missing = "invalid block texture " + source + ": " + invalid.getMessage();
                    break;
                }
                String key = "voxelcore.b_" + shortId + "_" + ("*".equals(face) ? "all" : face);
                String asset = "textures/voxelcore/b_" + shortId + "_" + ("*".equals(face) ? "all" : face);
                if (textureData.has(key) || bedrockAssets.containsKey(asset + ".png")) {
                    throw new IOException("Generated Bedrock block texture collides with authored asset " + key);
                }
                generatedTextures.put(asset + ".png", png);
                JsonObject entry = new JsonObject();
                entry.addProperty("textures", asset);
                generatedTerrain.add(key, entry);
                JsonObject material = new JsonObject();
                material.addProperty("texture", key);
                material.addProperty("render_method", "alpha_test");
                material.addProperty("face_dimming", true);
                material.addProperty("ambient_occlusion", true);
                materials.add(face, material);
            }
            if (missing != null) {
                unsupported.add("block " + block.id() + ": " + missing);
                continue;
            }

            String carrier = BlockCarrierStates.blockName(state);
            String properties = BlockCarrierStates.properties(state);
            JsonObject carrierEntry;
            if (blocks.has(carrier)) carrierEntry = blocks.getAsJsonObject(carrier);
            else {
                carrierEntry = new JsonObject();
                // Geyser registers one custom block with state-specific permutations
                // rather than overriding the entire vanilla carrier family.
                carrierEntry.addProperty("name", "voxelcore_" + carrier.substring("minecraft:".length()));
                carrierEntry.addProperty("included_in_creative_inventory", false);
                carrierEntry.addProperty("only_override_states", true);
                carrierEntry.add("state_overrides", new JsonObject());
                blocks.add(carrier, carrierEntry);
            }
            JsonObject overrides = carrierEntry.getAsJsonObject("state_overrides");
            if (overrides.has(properties))
                throw new IOException("Duplicate Bedrock custom block carrier state " + state);
            JsonObject options = new JsonObject();
            options.addProperty("display_name", block.displayName() == null ? block.id().toString() : block.displayName());
            options.addProperty("unit_cube", true);
            options.addProperty("place_air", false);
            options.add("material_instances", materials);
            overrides.add(properties, options);
            generatedTextures.forEach(bedrockAssets::put);
            for (Map.Entry<String, com.google.gson.JsonElement> item : generatedTerrain.entrySet())
                textureData.add(item.getKey(), item.getValue());
            count++;
        }
        if (count > 0)
            bedrockAssets.put("textures/terrain_texture.json", bytes(terrain));
        return new Output(bytes(mappings), count);
    }

    private static String faceTexture(BlockDefinition definition, String face) {
        Map<String, String> textures = definition.textures();
        if (definition.model() == BlockModelPreset.CUBE_ALL) return textures.get("all");
        if (definition.model() == BlockModelPreset.CUBE_COLUMN) {
            if ("up".equals(face)) return textures.get("top");
            if ("down".equals(face)) return textures.get("bottom");
            return textures.get("side");
        }
        return textures.get(face);
    }

    private static JsonObject terrain(byte[] authored) throws IOException {
        if (authored != null) {
            try {
                JsonObject existing = JsonParser.parseString(new String(authored, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!existing.has("texture_data") || !existing.get("texture_data").isJsonObject())
                    throw new IOException("Authored terrain_texture.json is missing texture_data");
                return existing;
            } catch (RuntimeException invalid) {
                throw new IOException("Invalid authored textures/terrain_texture.json", invalid);
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("resource_pack_name", "VoxelCore");
        result.addProperty("texture_name", "atlas.terrain");
        result.add("texture_data", new JsonObject());
        return result;
    }

    private static String hash(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte value : hash) out.append(String.format(Locale.ROOT, "%02x", value & 255));
            return out.toString();
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    private static byte[] bytes(JsonObject object) {
        return JSON.toJson(object).getBytes(StandardCharsets.UTF_8);
    }
}
