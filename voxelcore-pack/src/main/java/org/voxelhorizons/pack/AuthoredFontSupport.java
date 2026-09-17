package org.voxelhorizons.pack;

import org.voxelhorizons.content.pack.ContentPack;
import org.voxelhorizons.content.pack.ContentPackDiscovery;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parsing and deterministic merge support for authored Minecraft font JSON files. */
final class AuthoredFontSupport {
    static final String DEFAULT_FONT_PATH = "assets/minecraft/font/default.json";

    private AuthoredFontSupport() {}

    static Set<Integer> reservedCodePoints(List<ContentPack> packs) {
        Set<Integer> reserved = new LinkedHashSet<Integer>();
        for (ContentPack pack : packs) {
            Path assets = pack.root().resolve("assets");
            if (!Files.isDirectory(assets)) continue;
            List<Path> fonts = new ArrayList<Path>();
            collectFontFiles(assets, assets, fonts);
            Collections.sort(fonts, new Comparator<Path>() {
                @Override public int compare(Path left, Path right) {
                    return left.toString().compareTo(right.toString());
                }
            });
            for (Path font : fonts) reserve(parse(read(font), font.toString()), font.toString(), reserved);
        }
        return reserved;
    }

    static byte[] mergeDefault(byte[] authored, byte[] generated) {
        Map<String, Object> authoredRoot = parse(authored, "authored " + DEFAULT_FONT_PATH);
        Map<String, Object> generatedRoot = parse(generated, "generated " + DEFAULT_FONT_PATH);
        List<Object> providers = providers(authoredRoot, "authored " + DEFAULT_FONT_PATH);
        providers.addAll(providers(generatedRoot, "generated " + DEFAULT_FONT_PATH));
        Map<String, Object> merged = new LinkedHashMap<String, Object>(authoredRoot);
        merged.put("providers", providers);
        return (json(merged, 0) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static void collectFontFiles(Path assets, Path root, List<Path> out) {
        try {
            DirectoryStream<Path> stream = Files.newDirectoryStream(root);
            try {
                for (Path child : stream) {
                    if (Files.isSymbolicLink(child)) {
                        throw new JavaPackCompileException("Symbolic links are not supported in authored fonts: " + child);
                    }
                    if (Files.isDirectory(child)) collectFontFiles(assets, child, out);
                    else if (Files.isRegularFile(child) && child.getFileName().toString().endsWith(".json")
                            && child.getParent() != null && containsFontDirectory(assets, child.getParent())) {
                        out.add(child);
                    }
                }
            } finally {
                stream.close();
            }
        } catch (IOException exception) {
            throw new JavaPackCompileException("Unable to discover authored fonts under " + root, exception);
        }
    }

    private static boolean containsFontDirectory(Path assets, Path parent) {
        Path relative = assets.relativize(parent);
        for (Path part : relative) if ("font".equals(part.toString())) return true;
        return false;
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new JavaPackCompileException("Unable to read authored font " + path, exception);
        }
    }

    private static Map<String, Object> parse(byte[] bytes, String source) {
        Object loaded;
        try (InputStream input = new ByteArrayInputStream(bytes)) {
            loaded = ContentPackDiscovery.yaml().load(input);
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        } catch (RuntimeException exception) {
            throw new JavaPackCompileException("Invalid font JSON in " + source + ": " + exception.getMessage(), exception);
        }
        if (!(loaded instanceof Map)) throw new JavaPackCompileException("Font JSON must be an object: " + source);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) loaded).entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                throw new JavaPackCompileException("Font JSON keys must be strings: " + source);
            }
            result.put((String) entry.getKey(), entry.getValue());
        }
        providers(result, source);
        return result;
    }

    private static List<Object> providers(Map<String, Object> root, String source) {
        Object value = root.get("providers");
        if (!(value instanceof List)) throw new JavaPackCompileException("Font JSON requires a providers array: " + source);
        return new ArrayList<Object>((List<?>) value);
    }

    private static void reserve(Map<String, Object> root, String source, Set<Integer> reserved) {
        for (Object rawProvider : providers(root, source)) {
            if (!(rawProvider instanceof Map)) {
                throw new JavaPackCompileException("Font providers must be objects: " + source);
            }
            Map<?, ?> provider = (Map<?, ?>) rawProvider;
            Object chars = provider.get("chars");
            if (chars != null) {
                if (!(chars instanceof List)) throw new JavaPackCompileException("Font provider chars must be an array: " + source);
                for (Object row : (List<?>) chars) reserveString(row, source, reserved);
            }
            Object advances = provider.get("advances");
            if (advances != null) {
                if (!(advances instanceof Map)) throw new JavaPackCompileException("Font provider advances must be an object: " + source);
                for (Object character : ((Map<?, ?>) advances).keySet()) reserveString(character, source, reserved);
            }
        }
    }

    private static void reserveString(Object value, String source, Set<Integer> reserved) {
        if (!(value instanceof String)) throw new JavaPackCompileException("Font characters must be strings: " + source);
        String text = (String) value;
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            reserved.add(Integer.valueOf(codePoint));
            index += Character.charCount(codePoint);
        }
    }

    private static String json(Object value, int depth) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + escape((String) value) + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof List) {
            StringBuilder out = new StringBuilder("[");
            List<?> values = (List<?>) value;
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) out.append(',');
                out.append('\n').append(indent(depth + 1)).append(json(values.get(i), depth + 1));
            }
            if (!values.isEmpty()) out.append('\n').append(indent(depth));
            return out.append(']').toString();
        }
        if (value instanceof Map) {
            StringBuilder out = new StringBuilder("{");
            int index = 0;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) throw new JavaPackCompileException("JSON object key is not a string");
                if (index++ > 0) out.append(',');
                out.append('\n').append(indent(depth + 1)).append('"').append(escape((String) entry.getKey()))
                        .append("\": ").append(json(entry.getValue(), depth + 1));
            }
            if (!((Map<?, ?>) value).isEmpty()) out.append('\n').append(indent(depth));
            return out.append('}').toString();
        }
        throw new JavaPackCompileException("Unsupported value in font JSON: " + value.getClass().getName());
    }

    private static String indent(int depth) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < depth * 2; i++) out.append(' ');
        return out.toString();
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (character < 0x20) out.append(String.format("\\u%04X", (int) character));
                    else out.append(character);
            }
        }
        return out.toString();
    }
}
