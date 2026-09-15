package org.voxelhorizons.pack;

import org.voxelhorizons.content.ContentID;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Immutable lookup of compiled UI glyphs and their stable Unicode allocations. */
public final class UiGlyphRegistry {
    private final Map<ContentID, UiGlyphDefinition> entries;

    UiGlyphRegistry(Map<ContentID, UiGlyphDefinition> entries) {
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<ContentID, UiGlyphDefinition>(entries));
    }

    public Map<ContentID, UiGlyphDefinition> entries() { return entries; }
    public Optional<UiGlyphDefinition> get(ContentID id) { return Optional.ofNullable(entries.get(id)); }
    public int size() { return entries.size(); }

    /**
     * Resolves :name: aliases when the value is unique, plus always-unambiguous
     * :namespace/name: aliases. This is used by VoxelCore-owned titles and integrations.
     */
    public String resolveAliases(String input) {
        if (input == null || input.indexOf(':') < 0) return input;
        String resolved = input;
        Map<String, UiGlyphDefinition> unique = new LinkedHashMap<String, UiGlyphDefinition>();
        java.util.Set<String> ambiguous = new java.util.HashSet<String>();
        for (UiGlyphDefinition glyph : entries.values()) {
            String value = glyph.id().value();
            if (unique.containsKey(value)) ambiguous.add(value);
            else unique.put(value, glyph);
            resolved = resolved.replace(":" + glyph.id().namespace() + "/" + value + ":", glyph.character());
        }
        for (Map.Entry<String, UiGlyphDefinition> entry : unique.entrySet()) {
            if (!ambiguous.contains(entry.getKey())) {
                resolved = resolved.replace(":" + entry.getKey() + ":", entry.getValue().character());
            }
        }
        return resolved;
    }
}
