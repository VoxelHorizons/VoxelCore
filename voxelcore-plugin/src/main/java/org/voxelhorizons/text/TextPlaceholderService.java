package org.voxelhorizons.text;

import org.voxelhorizons.pack.UiGlyphRegistry;
import org.voxelhorizons.pack.UiTextResolver;

/** Thread-safe runtime entry point for UI and offset placeholder resolution. */
public final class TextPlaceholderService {
    private volatile UiTextResolver resolver;

    public TextPlaceholderService(UiGlyphRegistry glyphs) {
        update(glyphs);
    }

    public String resolve(String input) {
        return resolver.resolve(input);
    }

    public void update(UiGlyphRegistry glyphs) {
        resolver = new UiTextResolver(glyphs);
    }
}
