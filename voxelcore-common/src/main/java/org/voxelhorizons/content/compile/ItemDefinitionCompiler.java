package org.voxelhorizons.content.compile;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.item.ItemRenderDefinition;
import org.voxelhorizons.content.item.ItemType;
import org.voxelhorizons.content.item.RawItemDefinition;
import org.voxelhorizons.content.item.RawItemRenderDefinition;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Compiles authoring definitions into an immutable runtime registry. */
public final class ItemDefinitionCompiler {

    private final ItemInheritanceResolver inheritanceResolver;

    public ItemDefinitionCompiler() { this(new ItemInheritanceResolver()); }
    ItemDefinitionCompiler(ItemInheritanceResolver inheritanceResolver) { this.inheritanceResolver = inheritanceResolver; }

    public ItemDefinitionRegistry compile(Collection<RawItemDefinition> rawDefinitions) {
        if (rawDefinitions == null) throw new ContentCompileException("Item definitions cannot be null");
        Map<ContentID, RawItemDefinition> indexed = new LinkedHashMap<ContentID, RawItemDefinition>();
        for (RawItemDefinition definition : rawDefinitions) {
            if (definition == null || definition.id() == null) throw new ContentCompileException("Item definition and id cannot be null");
            if (indexed.put(definition.id(), definition) != null) throw new ContentCompileException("Duplicate item id: " + definition.id());
        }
        return compile(indexed);
    }

    public ItemDefinitionRegistry compile(Map<ContentID, RawItemDefinition> rawDefinitions) {
        if (rawDefinitions == null) throw new ContentCompileException("Item definitions cannot be null");
        Map<ContentID, RawItemDefinition> checked = new LinkedHashMap<ContentID, RawItemDefinition>();
        for (Map.Entry<ContentID, RawItemDefinition> entry : rawDefinitions.entrySet()) {
            ContentID key = entry.getKey();
            RawItemDefinition definition = entry.getValue();
            if (key == null || definition == null || definition.id() == null) throw new ContentCompileException("Item definition keys and ids cannot be null");
            if (!key.equals(definition.id())) throw new ContentCompileException("Item map key " + key + " does not match definition id " + definition.id());
            checked.put(key, definition);
        }

        Map<ContentID, RawItemDefinition> resolved = inheritanceResolver.resolveAll(checked);
        Map<ContentID, ItemDefinition> compiled = new LinkedHashMap<ContentID, ItemDefinition>();
        for (Map.Entry<ContentID, RawItemDefinition> entry : resolved.entrySet()) compiled.put(entry.getKey(), compileResolved(entry.getValue()));
        return new ItemDefinitionRegistry(compiled);
    }

    private ItemDefinition compileResolved(RawItemDefinition raw) {
        boolean abstractDefinition = raw.abstractDefinition() != null && raw.abstractDefinition().booleanValue();
        if (!abstractDefinition && (raw.material() == null || raw.material().trim().isEmpty())) {
            throw new ContentCompileException("Item " + raw.id() + " has no material after inheritance resolution");
        }
        ItemType type = raw.type() == null ? ItemType.ITEM : raw.type();
        boolean bound = raw.bound() != null && raw.bound().booleanValue();
        ItemRenderDefinition render = compileRender(raw.render());
        return new ItemDefinition(raw.id(), type, raw.material(), raw.displayName(),
                raw.lore() == null ? Collections.<String>emptyList() : raw.lore(), bound, abstractDefinition,
                Optional.ofNullable(raw.parent()), render,
                raw.properties() == null ? Collections.<String, Object>emptyMap() : raw.properties(), raw.events());
    }

    private ItemRenderDefinition compileRender(RawItemRenderDefinition raw) {
        if (raw == null) return null;
        return new ItemRenderDefinition(raw.model(), raw.unbreakable(), raw.durability(), raw.attributes(),
                raw.customModelData(), raw.oversizedInGui() != null && raw.oversizedInGui().booleanValue(),
                raw.rule());
    }
}
