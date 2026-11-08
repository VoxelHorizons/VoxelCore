package org.voxelhorizons.pack;

import org.voxelhorizons.content.pack.ContentPackDiscovery;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The exact finite set of RGB values that the Java colour picker can produce.
 * Only colours from the effective server configuration are pre-baked for
 * Bedrock, never all 16,777,216 possible custom_model_data colours.
 *
 * No Bukkit dependency: the pack compiler also runs in standalone tests.
 */
final class BedrockPaletteConfig {
    private BedrockPaletteConfig() { }

    static List<Integer> read(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) return Collections.emptyList();
        if (Files.isSymbolicLink(file)) throw new IOException("Color picker config must not be a symlink: " + file);
        Object parsed;
        try (InputStream input = Files.newInputStream(file)) {
            parsed = ContentPackDiscovery.yaml().load(input);
        } catch (RuntimeException invalid) {
            throw new IOException("Unable to parse Bedrock colour palette " + file, invalid);
        }
        if (!(parsed instanceof Map))
            throw new IOException("colorpicker.yml must be a YAML mapping: " + file);
        Map<?, ?> root = (Map<?, ?>) parsed;
        if (Boolean.FALSE.equals(root.get("enabled"))) return Collections.emptyList();
        Object raw = root.get("palettes");
        if (!(raw instanceof Map) || ((Map<?, ?>) raw).isEmpty())
            throw new IOException("colorpicker.yml palettes must be a nonempty mapping: " + file);
        if (((Map<?, ?>) raw).size() > 9)
            throw new IOException("colorpicker.yml exceeds nine primary palettes: " + file);
        TreeSet<Integer> colours = new TreeSet<Integer>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            String name = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof Map))
                throw new IOException("Invalid palette " + name + " in " + file);
            Map<?, ?> palette = (Map<?, ?>) entry.getValue();
            Object shades = palette.get("shades");
            if (!(shades instanceof List) || ((List<?>) shades).isEmpty()
                    || ((List<?>) shades).size() > 9)
                throw new IOException("Palette " + name + " must contain 1..9 shades");
            for (Object shade : (List<?>) shades) colours.add(Integer.valueOf(rgb(shade, name)));
            if (palette.containsKey("button-color"))
                colours.add(Integer.valueOf(rgb(palette.get("button-color"), name + ".button-color")));
        }
        if (colours.size() > 90)
            throw new IOException("Colour picker exceeds the 90 unique-shade Bedrock limit");
        return Collections.unmodifiableList(new ArrayList<Integer>(colours));
    }

    static int rgb(Object input, String context) throws IOException {
        if (!(input instanceof String))
            throw new IOException("RGB shade for " + context + " must be a quoted #RRGGBB string");
        String value = ((String) input).trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (!value.matches("[0-9a-fA-F]{6}"))
            throw new IOException("Invalid RGB shade for " + context + ": " + input);
        return Integer.parseInt(value, 16);
    }

    static String selector(int rgb) {
        return String.format(java.util.Locale.ROOT, "voxelcore:rgb:%06X", rgb & 0xFFFFFF);
    }
}
