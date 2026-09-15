package org.voxelhorizons.pack;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.load.ContentLoadException;
import org.voxelhorizons.content.pack.ContentPack;
import org.voxelhorizons.content.pack.ContentPackDiscovery;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses compact UI font-image content and reconciles stable private-use Unicode allocations. */
public final class UiGlyphLoader {
    private static final int FIRST_CODE_POINT = 0xE000;
    private static final int LAST_CODE_POINT = 0xF7FF;

    public UiGlyphRegistry load(Path contentRoot, Path allocationFile, boolean persist) {
        List<ContentPack> packs = new ContentPackDiscovery().discover(contentRoot);
        Map<ContentID, RawGlyph> raw = new LinkedHashMap<ContentID, RawGlyph>();
        for (ContentPack pack : packs) {
            Path root = pack.root().resolve("content");
            if (!Files.isDirectory(root)) continue;
            for (Path file : contentFiles(root)) parseFile(pack, file, raw);
        }

        AllocationState state = readAllocations(allocationFile);
        Set<Integer> used = new HashSet<Integer>();
        Map<ContentID, Integer> active = new LinkedHashMap<ContentID, Integer>();
        List<ContentID> ids = new ArrayList<ContentID>(raw.keySet());
        Collections.sort(ids, byId());

        for (ContentID id : ids) {
            RawGlyph glyph = raw.get(id);
            Integer previous = state.active.containsKey(id) ? state.active.get(id) : state.inactive.get(id);
            if (glyph.explicitCodePoint != null && previous != null && !glyph.explicitCodePoint.equals(previous)) {
                throw new ContentLoadException("UI glyph " + id + " cannot change its stable symbol from "
                        + codePoint(previous.intValue()) + " to " + codePoint(glyph.explicitCodePoint.intValue()));
            }
            Integer allocated = glyph.explicitCodePoint == null ? previous : glyph.explicitCodePoint;
            if (allocated != null) {
                requirePrivateUse(allocated.intValue(), id);
                for (Map.Entry<ContentID, Integer> reserved : state.inactive.entrySet()) {
                    if (!reserved.getKey().equals(id) && reserved.getValue().equals(allocated)) {
                        throw new ContentLoadException("UI glyph codepoint " + codePoint(allocated.intValue())
                                + " is reserved by inactive glyph " + reserved.getKey());
                    }
                }
                if (!used.add(allocated)) throw new ContentLoadException("Duplicate UI glyph codepoint "
                        + codePoint(allocated.intValue()) + " for " + id);
                active.put(id, allocated);
            }
        }
        for (ContentID id : ids) {
            if (active.containsKey(id)) continue;
            int value = FIRST_CODE_POINT;
            while (value <= LAST_CODE_POINT && (used.contains(Integer.valueOf(value))
                    || state.inactive.containsValue(Integer.valueOf(value)))) value++;
            if (value > LAST_CODE_POINT) throw new ContentLoadException("No private-use Unicode codepoints remain for UI glyph " + id);
            active.put(id, Integer.valueOf(value));
            used.add(Integer.valueOf(value));
        }

        Map<ContentID, Integer> inactive = new LinkedHashMap<ContentID, Integer>(state.inactive);
        for (Map.Entry<ContentID, Integer> old : state.active.entrySet()) {
            if (!active.containsKey(old.getKey())) inactive.put(old.getKey(), old.getValue());
        }
        for (ContentID id : active.keySet()) inactive.remove(id);

        Map<ContentID, UiGlyphDefinition> resolved = new LinkedHashMap<ContentID, UiGlyphDefinition>();
        for (ContentID id : ids) {
            RawGlyph glyph = raw.get(id);
            resolved.put(id, new UiGlyphDefinition(id, glyph.texture, glyph.scaleRatio, glyph.yPosition,
                    active.get(id).intValue()));
        }
        if (persist) writeAllocations(allocationFile, active, inactive);
        return new UiGlyphRegistry(resolved);
    }

    private static void parseFile(ContentPack pack, Path file, Map<ContentID, RawGlyph> out) {
        Object loaded;
        try (InputStream input = Files.newInputStream(file)) {
            loaded = ContentPackDiscovery.yaml().load(input);
        } catch (IOException exception) {
            throw new ContentLoadException("Unable to read UI definitions: " + file, exception);
        } catch (RuntimeException exception) {
            throw new ContentLoadException("Invalid YAML in UI definitions: " + file, exception);
        }
        if (!(loaded instanceof Map)) throw new ContentLoadException("Definition file must be a mapping: " + file);
        Object section = ((Map<?, ?>) loaded).get("ui");
        if (section == null) return;
        if (!(section instanceof Map)) throw new ContentLoadException("'ui' must be a mapping in " + file);
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) section).entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Map)) {
                throw new ContentLoadException("Each UI glyph must be a named mapping in " + file);
            }
            ContentID id = ContentID.parse((String) entry.getKey(), pack.manifest().namespace());
            if (!id.namespace().equals(pack.manifest().namespace())) {
                throw new ContentLoadException("UI glyph " + id + " in " + file + " must use pack namespace "
                        + pack.manifest().namespace());
            }
            if (out.containsKey(id)) throw new ContentLoadException("Duplicate UI glyph id " + id + " in " + file);
            out.put(id, parseGlyph(pack, file, id, (Map<?, ?>) entry.getValue()));
        }
    }

    private static RawGlyph parseGlyph(ContentPack pack, Path file, ContentID id, Map<?, ?> map) {
        rejectUnknown(map, file, id.toString(), "path", "scale_ratio", "y_position", "symbol");
        String authoredPath = requireString(map.get("path"), "path", id, file).replace('\\', '/');
        if (authoredPath.startsWith("/") || authoredPath.contains("..")
                || !authoredPath.toLowerCase(java.util.Locale.ROOT).endsWith(".png")
                || !authoredPath.matches("[a-z0-9/._-]+")) {
            throw new ContentLoadException("UI glyph " + id + " path must be a safe lowercase .png path in " + file);
        }
        String resourcePath = authoredPath.substring(0, authoredPath.length() - 4);
        Path textureFile = pack.root().resolve("assets").resolve(pack.manifest().namespace())
                .resolve("textures").resolve(authoredPath);
        if (!Files.isRegularFile(textureFile)) {
            throw new ContentLoadException("UI glyph " + id + " references missing texture. Expected " + textureFile);
        }

        final BufferedImage image;
        try {
            image = ImageIO.read(textureFile.toFile());
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1
                    || image.getWidth() > 256 || image.getHeight() > 256) {
                throw new ContentLoadException("UI glyph texture must be a readable PNG no larger than 256x256: " + textureFile);
            }
        } catch (IOException exception) {
            throw new ContentLoadException("Unable to inspect UI glyph texture " + textureFile, exception);
        }
        ensureVisible(image, id);

        int scaleRatio = map.containsKey("scale_ratio")
                ? requireInteger(map.get("scale_ratio"), "scale_ratio", id, file)
                : image.getHeight();
        if (scaleRatio < 1 || scaleRatio > 256) {
            throw new ContentLoadException("scale_ratio must be between 1 and 256 for " + id);
        }
        int yPosition = map.containsKey("y_position")
                ? requireInteger(map.get("y_position"), "y_position", id, file)
                : Math.min(8, scaleRatio);
        if (yPosition > scaleRatio) {
            throw new ContentLoadException("y_position must be lower than or equal to scale_ratio for " + id);
        }

        Integer explicit = null;
        if (map.containsKey("symbol")) {
            String symbol = requireString(map.get("symbol"), "symbol", id, file);
            if (symbol.codePointCount(0, symbol.length()) != 1) {
                throw new ContentLoadException("symbol must contain exactly one character for " + id);
            }
            explicit = Integer.valueOf(symbol.codePointAt(0));
            requirePrivateUse(explicit.intValue(), id);
        }
        return new RawGlyph(pack.manifest().namespace() + ":" + resourcePath, scaleRatio, yPosition, explicit);
    }

    private static void ensureVisible(BufferedImage image, ContentID id) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) != 0) return;
            }
        }
        throw new ContentLoadException("UI glyph " + id + " texture is fully transparent");
    }

    private static AllocationState readAllocations(Path file) {
        AllocationState state = new AllocationState();
        if (!Files.isRegularFile(file)) return state;
        Object loaded;
        try (InputStream input = Files.newInputStream(file)) {
            loaded = ContentPackDiscovery.yaml().load(input);
        } catch (IOException exception) {
            throw new ContentLoadException("Unable to read UI glyph allocations " + file, exception);
        }
        if (!(loaded instanceof Map)) throw new ContentLoadException("UI glyph allocation manifest must be a mapping: " + file);
        Map<?, ?> root = (Map<?, ?>) loaded;
        Object schema = root.get("schema");
        if (!(schema instanceof Number) || ((Number) schema).intValue() != 1) {
            throw new ContentLoadException("Unsupported UI glyph allocation schema in " + file);
        }
        readAllocationMap(root.get("glyphs"), state.active, file);
        readAllocationMap(root.get("inactive"), state.inactive, file);
        return state;
    }

    private static void readAllocationMap(Object raw, Map<ContentID, Integer> target, Path file) {
        if (raw == null) return;
        if (!(raw instanceof Map)) throw new ContentLoadException("UI glyph allocation section must be a mapping in " + file);
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Number)) {
                throw new ContentLoadException("UI glyph allocations must map ContentIDs to integers in " + file);
            }
            ContentID id = ContentID.parse((String) entry.getKey(), "voxelhorizons");
            int value = ((Number) entry.getValue()).intValue();
            requirePrivateUse(value, id);
            target.put(id, Integer.valueOf(value));
        }
    }

    private static void writeAllocations(Path file, Map<ContentID, Integer> active, Map<ContentID, Integer> inactive) {
        StringBuilder out = new StringBuilder("schema: 1\nglyphs:\n");
        appendAllocations(out, active);
        out.append("inactive:\n");
        appendAllocations(out, inactive);
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.write(file, out.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException exception) {
            throw new ContentLoadException("Unable to persist UI glyph allocations " + file, exception);
        }
    }

    private static void appendAllocations(StringBuilder out, Map<ContentID, Integer> values) {
        List<ContentID> ids = new ArrayList<ContentID>(values.keySet());
        Collections.sort(ids, byId());
        if (ids.isEmpty()) out.append("  {}\n");
        for (ContentID id : ids) out.append("  '").append(id).append("': ").append(values.get(id)).append('\n');
    }

    private static List<Path> contentFiles(Path root) {
        List<Path> files = new ArrayList<Path>();
        try {
            java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(root);
            try {
                for (Path path : stream) {
                    if (Files.isDirectory(path)) files.addAll(contentFiles(path));
                    else {
                        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                        if (name.endsWith(".yml") || name.endsWith(".yaml")) files.add(path);
                    }
                }
            } finally { stream.close(); }
        } catch (IOException exception) {
            throw new ContentLoadException("Unable to discover UI definitions under " + root, exception);
        }
        Collections.sort(files, new Comparator<Path>() {
            @Override public int compare(Path left, Path right) { return left.toString().compareTo(right.toString()); }
        });
        return files;
    }

    private static Comparator<ContentID> byId() {
        return new Comparator<ContentID>() {
            @Override public int compare(ContentID left, ContentID right) {
                return left.toString().compareTo(right.toString());
            }
        };
    }

    private static void requirePrivateUse(int value, ContentID id) {
        if (value < FIRST_CODE_POINT || value > LAST_CODE_POINT) {
            throw new ContentLoadException("UI glyph " + id + " symbol must be in U+E000..U+F7FF");
        }
    }

    private static int requireInteger(Object value, String key, ContentID id, Path file) {
        if (!(value instanceof Number)) throw new ContentLoadException(key + " must be an integer for " + id + " in " + file);
        double number = ((Number) value).doubleValue();
        int integer = ((Number) value).intValue();
        if (number != integer) throw new ContentLoadException(key + " must be a whole number for " + id + " in " + file);
        return integer;
    }

    private static String requireString(Object value, String key, ContentID id, Path file) {
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new ContentLoadException(key + " must be a non-empty string for " + id + " in " + file);
        }
        return ((String) value).trim();
    }

    private static void rejectUnknown(Map<?, ?> map, Path file, String context, String... allowed) {
        Set<String> keys = new HashSet<String>();
        Collections.addAll(keys, allowed);
        for (Object key : map.keySet()) {
            if (!(key instanceof String) || !keys.contains(key)) {
                throw new ContentLoadException("Unsupported key '" + key + "' in " + context + " at " + file);
            }
        }
    }

    private static String codePoint(int value) { return String.format("U+%04X", value); }

    private static final class RawGlyph {
        private final String texture;
        private final int scaleRatio;
        private final int yPosition;
        private final Integer explicitCodePoint;
        private RawGlyph(String texture, int scaleRatio, int yPosition, Integer explicitCodePoint) {
            this.texture = texture; this.scaleRatio = scaleRatio; this.yPosition = yPosition;
            this.explicitCodePoint = explicitCodePoint;
        }
    }

    private static final class AllocationState {
        private final Map<ContentID, Integer> active = new LinkedHashMap<ContentID, Integer>();
        private final Map<ContentID, Integer> inactive = new LinkedHashMap<ContentID, Integer>();
    }
}
