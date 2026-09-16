package org.voxelhorizons.content.compile;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.action.EventActions;
import org.voxelhorizons.content.block.BlockDefinition;
import org.voxelhorizons.content.block.BlockDefinitionRegistry;
import org.voxelhorizons.content.block.BlockMethod;
import org.voxelhorizons.content.block.BlockModelPreset;
import org.voxelhorizons.content.block.RawBlockDefinition;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Resolves block inheritance and validates model texture presets. */
public final class BlockDefinitionCompiler {
    private enum State { VISITING, RESOLVED }

    public BlockDefinitionRegistry compile(Collection<RawBlockDefinition> rawDefinitions) {
        Map<ContentID, RawBlockDefinition> indexed = new LinkedHashMap<ContentID, RawBlockDefinition>();
        for (RawBlockDefinition definition : rawDefinitions) {
            if (indexed.put(definition.id(), definition) != null) {
                throw new ContentCompileException("Duplicate block id: " + definition.id());
            }
        }
        Map<ContentID, RawBlockDefinition> resolved = new LinkedHashMap<ContentID, RawBlockDefinition>();
        Map<ContentID, State> states = new HashMap<ContentID, State>();
        for (ContentID id : indexed.keySet()) resolve(id, indexed, resolved, states, new ArrayDeque<ContentID>());

        Map<ContentID, BlockDefinition> compiled = new LinkedHashMap<ContentID, BlockDefinition>();
        for (RawBlockDefinition raw : resolved.values()) compiled.put(raw.id(), compileResolved(raw));
        return new BlockDefinitionRegistry(compiled);
    }

    private RawBlockDefinition resolve(ContentID id, Map<ContentID, RawBlockDefinition> definitions,
                                       Map<ContentID, RawBlockDefinition> resolved, Map<ContentID, State> states,
                                       Deque<ContentID> path) {
        if (states.get(id) == State.RESOLVED) return resolved.get(id);
        if (states.get(id) == State.VISITING) throw new ContentCompileException("Circular block inheritance detected at " + id + ": " + path);
        RawBlockDefinition child = definitions.get(id);
        states.put(id, State.VISITING);
        path.addLast(id);
        RawBlockDefinition result = child;
        if (child.parent() != null) {
            RawBlockDefinition parent = definitions.get(child.parent());
            if (parent == null) throw new ContentCompileException("Unable to compile " + id + ": missing block parent " + child.parent());
            result = merge(resolve(parent.id(), definitions, resolved, states, path), child);
        }
        path.removeLast();
        states.put(id, State.RESOLVED);
        resolved.put(id, result);
        return result;
    }

    private static RawBlockDefinition merge(RawBlockDefinition parent, RawBlockDefinition child) {
        Map<String, String> textures = null;
        if (parent.textures() != null || child.textures() != null) {
            textures = new LinkedHashMap<String, String>();
            if (parent.textures() != null) textures.putAll(parent.textures());
            if (child.textures() != null) textures.putAll(child.textures());
        }
        return new RawBlockDefinition(child.id(), child.parent(), choose(child.abstractDefinition(), parent.abstractDefinition()),
                choose(child.method(), parent.method()), choose(child.model(), parent.model()),
                child.texture() != null ? child.texture() : parent.texture(), textures,
                choose(child.hardness(), parent.hardness()), choose(child.blastResistance(), parent.blastResistance()),
                choose(child.explosionImmune(), parent.explosionImmune()),
                choose(child.dropWhenMined(), parent.dropWhenMined()),
                child.dropItem() != null ? child.dropItem() : parent.dropItem(),
                child.silkTouchItem() != null ? child.silkTouchItem() : parent.silkTouchItem(),
                child.events() != null ? child.events() : parent.events());
    }

    private static <T> T choose(T child, T parent) { return child != null ? child : parent; }

    private static BlockDefinition compileResolved(RawBlockDefinition raw) {
        boolean abstractDefinition = Boolean.TRUE.equals(raw.abstractDefinition());
        BlockMethod method = raw.method() == null ? BlockMethod.AUTO : raw.method();
        BlockModelPreset model = raw.model() == null ? BlockModelPreset.CUBE_ALL : raw.model();
        Map<String, String> textures = normalizedTextures(raw, model, abstractDefinition);
        double hardness = raw.hardness() == null ? 1.5D : raw.hardness().doubleValue();
        double resistance = raw.blastResistance() == null ? hardness * 3.0D : raw.blastResistance().doubleValue();
        boolean drop = raw.dropWhenMined() == null || raw.dropWhenMined().booleanValue();
        ContentID dropItem = raw.dropItem() == null && drop ? raw.id() : raw.dropItem();
        ContentID silk = raw.silkTouchItem() == null ? dropItem : raw.silkTouchItem();
        return new BlockDefinition(raw.id(), Optional.ofNullable(raw.parent()), abstractDefinition, method, model,
                textures, hardness, resistance, Boolean.TRUE.equals(raw.explosionImmune()), drop,
                dropItem, silk, raw.events() == null ? EventActions.empty() : raw.events());
    }

    private static Map<String, String> normalizedTextures(RawBlockDefinition raw, BlockModelPreset model,
                                                           boolean abstractDefinition) {
        Map<String, String> authored = raw.textures() == null
                ? new LinkedHashMap<String, String>() : new LinkedHashMap<String, String>(raw.textures());
        if (raw.texture() != null) authored.put("all", raw.texture());
        if (abstractDefinition && authored.isEmpty()) return Collections.emptyMap();
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (model == BlockModelPreset.CUBE_ALL) {
            String all = first(authored, "all", "side");
            require(all, raw, "texture or textures.all");
            out.put("all", all);
        } else if (model == BlockModelPreset.CUBE_COLUMN) {
            String side = first(authored, "side", "all");
            String top = first(authored, "top", "up", "all");
            String bottom = first(authored, "bottom", "down", "top", "up", "all");
            require(side, raw, "textures.side"); require(top, raw, "textures.top");
            out.put("side", side); out.put("top", top); out.put("bottom", bottom);
        } else {
            for (String face : new String[]{"north", "south", "east", "west", "up", "down"}) {
                String value = first(authored, face, verticalAlias(face), "side", "all");
                require(value, raw, "textures." + face);
                out.put(face, value);
            }
        }
        return out;
    }

    private static String verticalAlias(String face) {
        if ("up".equals(face)) return "top";
        if ("down".equals(face)) return "bottom";
        return face;
    }

    private static String first(Map<String, String> values, String... keys) {
        for (String key : keys) if (values.get(key) != null) return values.get(key);
        return null;
    }

    private static void require(String value, RawBlockDefinition raw, String description) {
        if (value == null) throw new ContentCompileException("Block " + raw.id() + " requires " + description);
    }
}
