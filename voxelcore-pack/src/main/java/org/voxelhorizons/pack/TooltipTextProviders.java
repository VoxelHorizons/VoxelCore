package org.voxelhorizons.pack;

/** Generated text-row providers for subtitle popups, without any embedded image assets. */
final class TooltipTextProviders {
    private TooltipTextProviders() {}

    static String defaultProviders() {
        StringBuilder out = new StringBuilder();
        for (int line = 1; line <= 3; line++) {
            if (line > 1) out.append(",\n    ");
            out.append("{\"type\":\"bitmap\",\"file\":\"minecraft:font/ascii.png\",\"ascent\":")
                    .append(TooltipGlyphs.lineAscent(line)).append(",\"height\":4,\"chars\":");
            appendRows(out, TooltipGlyphs.fontRows(line));
            out.append('}');
        }
        return out.toString();
    }

    private static void appendRows(StringBuilder out, String[] rows) {
        out.append('[');
        for (int row = 0; row < rows.length; row++) {
            if (row > 0) out.append(',');
            out.append('"');
            String value = rows[row];
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (character == 0) out.append("\\u0000");
                else if (character == '\\') out.append("\\\\");
                else if (character == '"') out.append("\\\"");
                else out.append(character);
            }
            out.append('"');
        }
        out.append(']');
    }
}
