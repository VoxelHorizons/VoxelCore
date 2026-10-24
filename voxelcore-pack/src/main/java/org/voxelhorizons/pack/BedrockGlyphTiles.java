package org.voxelhorizons.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Rewrites wide glyphs (rank tags) in text bound for Bedrock players into the square tile
 * sequence the Bedrock pack defines for them, so they keep their Java proportions instead of
 * being squeezed into one square font cell. Loaded from the build's glyph-tiles.json; the
 * mapping is only valid once Geyser serves the pack built with it.
 */
public final class BedrockGlyphTiles {
    private static volatile Map<Character, String> tiles = Collections.emptyMap();

    private BedrockGlyphTiles() {}

    /** Load (or clear, when absent) the tile map; returns how many glyphs are tiled. */
    public static int load(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) { tiles = Collections.emptyMap(); return 0; }
        tiles = parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        return tiles.size();
    }

    static Map<Character, String> parse(String json) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            Map<Character, String> out = new HashMap<Character, String>();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("tiles").entrySet()) {
                int source = Integer.parseInt(entry.getKey(), 16);
                if (source < 0xE000 || source > 0xF8FF) throw new IOException("Tile source outside private use: " + entry.getKey());
                StringBuilder sequence = new StringBuilder();
                for (JsonElement tile : entry.getValue().getAsJsonArray()) {
                    int codePoint = Integer.parseInt(tile.getAsString(), 16);
                    if (codePoint < 0xF400 || codePoint > 0xF8FF) throw new IOException("Tile outside U+F400..U+F8FF: " + tile);
                    sequence.append((char) codePoint);
                }
                out.put(Character.valueOf((char) source), sequence.toString());
            }
            return Collections.unmodifiableMap(out);
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid " + BedrockPackCompiler.GLYPH_TILES + ": " + invalid.getMessage(), invalid);
        }
    }

    /** True when the text holds at least one tiled glyph (cheap pre-check before rewriting). */
    public static boolean affects(String text) {
        Map<Character, String> current = tiles;
        if (text == null || current.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) if (current.containsKey(Character.valueOf(text.charAt(i)))) return true;
        return false;
    }

    public static String apply(String text) {
        return apply(text, tiles);
    }

    static String apply(String text, Map<Character, String> map) {
        if (text == null || map.isEmpty()) return text;
        StringBuilder out = null;
        for (int i = 0; i < text.length(); i++) {
            String replacement = map.get(Character.valueOf(text.charAt(i)));
            if (replacement == null) { if (out != null) out.append(text.charAt(i)); continue; }
            if (out == null) out = new StringBuilder(text.length() + 8).append(text, 0, i);
            out.append(replacement);
        }
        return out == null ? text : out.toString();
    }
}
