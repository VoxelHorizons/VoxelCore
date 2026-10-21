package org.voxelhorizons.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Recursive, deterministic merge support for JSON-backed Minecraft resource-pack assets. */
final class JsonAssetCompatibility {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private JsonAssetCompatibility() {}

    static boolean isJsonAsset(String path) {
        return path.endsWith(".json") || path.endsWith(".mcmeta");
    }

    /**
     * Combines object members and appends arrays. Equal object values are kept once; incompatible
     * values at the same location fail with a JSON path so the source conflict is actionable.
     */
    static byte[] merge(byte[] existingBytes, byte[] incomingBytes, String path) {
        JsonElement existing = parse(existingBytes, path);
        JsonElement incoming = parse(incomingBytes, path);
        JsonElement merged = merge(existing, incoming, "$", path);
        return (GSON.toJson(merged) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static JsonElement merge(JsonElement existing, JsonElement incoming, String jsonPath, String assetPath) {
        if (existing.equals(incoming)) return existing;
        if (existing.isJsonObject() && incoming.isJsonObject()) {
            JsonObject result = existing.deepCopy();
            for (String key : incoming.getAsJsonObject().keySet()) {
                JsonElement incomingValue = incoming.getAsJsonObject().get(key);
                if (!result.has(key)) result.add(key, incomingValue.deepCopy());
                else result.add(key, merge(result.get(key), incomingValue, childPath(jsonPath, key), assetPath));
            }
            return result;
        }
        if (existing.isJsonArray() && incoming.isJsonArray()) {
            JsonArray result = existing.getAsJsonArray().deepCopy();
            // Arrays are ordered in pack metadata and may intentionally contain repeated values
            // (for example animation frame sequences), so preserve every incoming entry.
            for (JsonElement value : incoming.getAsJsonArray()) result.add(value.deepCopy());
            return result;
        }
        throw new JavaPackCompileException("Resource pack path collision at " + assetPath
                + ": incompatible JSON values at " + jsonPath);
    }

    private static JsonElement parse(byte[] bytes, String path) {
        try {
            JsonReader reader = new JsonReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
            reader.setLenient(false);
            JsonElement element = readElement(reader, "$", path);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new JavaPackCompileException("Invalid JSON at " + path + ": trailing content");
            }
            reader.close();
            return element;
        } catch (IOException | IllegalStateException | NumberFormatException exception) {
            throw new JavaPackCompileException("Invalid JSON at " + path + ": " + exception.getMessage(), exception);
        }
    }

    private static JsonElement readElement(JsonReader reader, String jsonPath, String assetPath) throws IOException {
        JsonToken token = reader.peek();
        switch (token) {
            case BEGIN_OBJECT:
                JsonObject object = new JsonObject();
                reader.beginObject();
                Set<String> keys = new HashSet<String>();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (!keys.add(key)) {
                        throw new JavaPackCompileException("Invalid JSON at " + assetPath
                                + ": duplicate key " + childPath(jsonPath, key));
                    }
                    object.add(key, readElement(reader, childPath(jsonPath, key), assetPath));
                }
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                JsonArray array = new JsonArray();
                reader.beginArray();
                int index = 0;
                while (reader.hasNext()) array.add(readElement(reader, jsonPath + "[" + index++ + "]", assetPath));
                reader.endArray();
                return array;
            case STRING:
                return new JsonPrimitive(reader.nextString());
            case NUMBER:
                return new JsonPrimitive(new java.math.BigDecimal(reader.nextString()));
            case BOOLEAN:
                return new JsonPrimitive(reader.nextBoolean());
            case NULL:
                reader.nextNull();
                return JsonNull.INSTANCE;
            default:
                throw new JavaPackCompileException("Invalid JSON at " + assetPath + ": unexpected token " + token);
        }
    }

    private static String childPath(String parent, String key) {
        if (key.matches("[A-Za-z_$][A-Za-z0-9_$]*")) return parent + "." + key;
        return parent + "['" + key.replace("\\", "\\\\").replace("'", "\\'") + "']";
    }
}
