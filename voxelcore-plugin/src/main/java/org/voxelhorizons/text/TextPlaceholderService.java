package org.voxelhorizons.text;

import org.voxelhorizons.pack.ContainerGuiLayout;
import org.voxelhorizons.pack.UiGlyphRegistry;
import org.voxelhorizons.pack.UiTextResolver;

/** Thread-safe runtime entry point for UI and offset placeholder resolution. */
public final class TextPlaceholderService {
    private volatile UiTextResolver resolver;

    public TextPlaceholderService(UiGlyphRegistry glyphs) {
        this(glyphs, ContainerGuiLayout.defaults());
    }

    public TextPlaceholderService(UiGlyphRegistry glyphs, ContainerGuiLayout containerGuiLayout) {
        update(glyphs, containerGuiLayout);
    }

    public String resolve(String input) {
        return resolver.resolve(input);
    }

    public String resolve(String input, boolean allowInline, boolean allowGui) {
        return resolver.resolve(input, allowInline, allowGui);
    }

    public void update(UiGlyphRegistry glyphs) {
        update(glyphs, ContainerGuiLayout.defaults());
    }

    public void update(UiGlyphRegistry glyphs, ContainerGuiLayout containerGuiLayout) {
        resolver = new UiTextResolver(glyphs, containerGuiLayout);
    }
}
