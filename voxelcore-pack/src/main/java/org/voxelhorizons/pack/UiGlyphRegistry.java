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
}
