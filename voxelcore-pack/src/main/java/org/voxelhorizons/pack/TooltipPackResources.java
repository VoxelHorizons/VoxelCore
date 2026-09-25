package org.voxelhorizons.pack;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Compiler-owned resource-pack assets and providers for popup tooltips. */
final class TooltipPackResources {
    private static final String LEFT_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAIAAAAmCAYAAADui0oEAAAAN0lEQVR42mMAAQEGgf2MIMKB4T0DE0jkP8NfGOMPGuMfkEFIzX8MNf/wqoGL4FE8mB0GBfDABACRh0dsZlnrPAAAAABJRU5ErkJggg==";
    private static final String CENTER_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAIAAAAmCAYAAADui0oEAAAAIklEQVR42mMQYBDYD8IMAQz/94MwTIRoxihDg6F+PxAjRAAEC0WhpHqqfQAAAABJRU5ErkJggg==";
    private static final String RIGHT_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAIAAAAmCAYAAADui0oEAAAAOElEQVR42mMQYBDYzwAETA4M78Ecpv8MfxlAAMj4g8b4B2XgU/MfQ80/vGrgIoQUD16HMUABPDABqHtHbZ2H6yEAAAAASUVORK5CYII=";
    private static final String RIGHT_OFFSET_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAQAAAAmCAYAAADjlTpDAAAANUlEQVR42mMIYPh/J4Dh/x0GKGBiQAMsBxgEPyILYKgYFRgVwCfA4sDwnp+BgYFhAwMjdhUAp/oIVpKr758AAAAASUVORK5CYII=";

    private TooltipPackResources() {}

    static Map<String, byte[]> entries() {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        entries.put("assets/voxelcore/textures/ui/tooltip/left.png", decode(LEFT_PNG));
        entries.put("assets/voxelcore/textures/ui/tooltip/center.png", decode(CENTER_PNG));
        entries.put("assets/voxelcore/textures/ui/tooltip/right.png", decode(RIGHT_PNG));
        entries.put("assets/voxelcore/textures/ui/tooltip/right_offset.png", decode(RIGHT_OFFSET_PNG));
        for (int line = 1; line <= 3; line++) {
            entries.put("assets/voxelcore/font/tooltip_line" + line + ".json",
                    fontFile(line).getBytes(StandardCharsets.UTF_8));
        }
        return entries;
    }

    static String defaultProviders() {
        StringBuilder out = new StringBuilder();
        appendBitmap(out, "voxelcore:ui/tooltip/left.png", 7, 19,
                String.valueOf((char) TooltipGlyphs.BACKGROUND_LEFT));
        appendBitmap(out, "voxelcore:ui/tooltip/center.png", 7, 19,
                String.valueOf((char) TooltipGlyphs.BACKGROUND_CENTER));
        appendBitmap(out, "voxelcore:ui/tooltip/right.png", 7, 19,
                String.valueOf((char) TooltipGlyphs.BACKGROUND_RIGHT));
        appendBitmap(out, "voxelcore:ui/tooltip/right_offset.png", 7, 19,
                String.valueOf((char) TooltipGlyphs.BACKGROUND_RIGHT_OFFSET));
        for (int line = 1; line <= 3; line++) {
            if (out.length() > 0) out.append(",\n    ");
            out.append("{\"type\":\"bitmap\",\"file\":\"minecraft:font/ascii.png\",\"ascent\":")
                    .append(TooltipGlyphs.lineAscent(line))
                    .append(",\"height\":4,\"chars\":");
            appendRows(out, TooltipGlyphs.fontRows(line));
            out.append('}');
        }
        return out.toString();
    }

    private static String fontFile(int line) {
        StringBuilder out = new StringBuilder("{\n  \"providers\": [\n    ");
        appendSpacingProvider(out);
        out.append(",\n    {\"type\":\"bitmap\",\"file\":\"minecraft:font/ascii.png\",\"ascent\":")
                .append(TooltipGlyphs.lineAscent(line)).append(",\"height\":4,\"chars\":");
        appendRows(out, vanillaRows());
        out.append("}\n  ]\n}\n");
        return out.toString();
    }

    private static void appendSpacingProvider(StringBuilder out) {
        out.append("{\"type\":\"space\",\"advances\":{\" \":4");
        for (Map.Entry<Integer, Integer> advance : UiSpacingGlyphs.advances().entrySet()) {
            out.append(",\"").append(new String(Character.toChars(advance.getKey().intValue())))
                    .append("\":").append(advance.getValue().intValue());
        }
        out.append("}}");
    }

    private static void appendBitmap(StringBuilder out, String file, int ascent, int height, String character) {
        if (out.length() > 0) out.append(",\n    ");
        out.append("{\"type\":\"bitmap\",\"file\":\"").append(file)
                .append("\",\"ascent\":").append(ascent)
                .append(",\"height\":").append(height)
                .append(",\"chars\":[\"").append(character).append("\"]}");
    }

    private static void appendRows(StringBuilder out, String[] rows) {
        out.append('[');
        for (int row = 0; row < rows.length; row++) {
            if (row > 0) out.append(',');
            out.append('\"');
            String value = rows[row];
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (character == 0) out.append("\\u0000");
                else if (character == '\\') out.append("\\\\");
                else if (character == '\"') out.append("\\\"");
                else out.append(character);
            }
            out.append('\"');
        }
        out.append(']');
    }

    private static String[] vanillaRows() {
        String[] rows = new String[16];
        for (int row = 0; row < 16; row++) {
            StringBuilder value = new StringBuilder(16);
            for (int column = 0; column < 16; column++) {
                int source = row * 16 + column;
                value.append(source >= 32 && source <= 126 ? (char) source : '\0');
            }
            rows[row] = value.toString();
        }
        return rows;
    }

    private static byte[] decode(String value) {
        return Base64.getDecoder().decode(value);
    }
}
