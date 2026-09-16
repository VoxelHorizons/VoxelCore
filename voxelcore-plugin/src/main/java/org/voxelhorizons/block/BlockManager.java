package org.voxelhorizons.block;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.block.BlockAllocation;
import org.voxelhorizons.content.block.BlockCarrierStates;
import org.voxelhorizons.content.block.BlockDefinition;
import org.voxelhorizons.content.runtime.ContentRuntime;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Places and identifies carrier-backed blocks without linking modern BlockData on legacy servers. */
public final class BlockManager {
    private final ContentRuntime content;
    private final boolean modern;

    public BlockManager(ContentRuntime content, boolean modern) {
        this.content = content;
        this.modern = modern;
    }

    public Optional<BlockDefinition> getDefinition(ContentID id) { return content.current().blocks().get(id); }

    public boolean place(Block block, ContentID id, boolean physics) {
        BlockDefinition definition = content.current().blocks().get(id).orElse(null);
        BlockAllocation allocation = content.current().blockAllocations().get(id).orElse(null);
        if (definition == null || definition.abstractDefinition() || allocation == null || !allocation.active()) return false;
        apply(block, BlockCarrierStates.state(allocation, modern), physics);
        return true;
    }

    public Optional<ContentID> identify(Block block) {
        return content.current().blockAllocations().identify(state(block), modern);
    }

    public String state(Block block) {
        if (!modern) return "legacy:" + block.getType().name() + ":" + (block.getData() & 0xFF);
        try {
            Method getBlockData = Block.class.getMethod("getBlockData");
            Object data = getBlockData.invoke(block);
            Method getAsString = data.getClass().getMethod("getAsString");
            return canonical(String.valueOf(getAsString.invoke(data)));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to inspect modern block data", exception);
        }
    }

    @SuppressWarnings("deprecation")
    private void apply(Block block, String state, boolean physics) {
        if (!modern) {
            String[] parts = state.split(":");
            Material material = Material.valueOf(parts[1]);
            byte data = (byte) Integer.parseInt(parts[2]);
            block.setType(material);
            block.setData(data, physics);
            return;
        }
        try {
            Method createBlockData = Bukkit.class.getMethod("createBlockData", String.class);
            Object data = createBlockData.invoke(null, state);
            Class<?> blockData = Class.forName("org.bukkit.block.data.BlockData");
            Method setBlockData = Block.class.getMethod("setBlockData", blockData, boolean.class);
            setBlockData.invoke(block, data, Boolean.valueOf(physics));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to apply modern carrier state " + state, exception);
        }
    }

    static String canonical(String state) {
        int bracket = state.indexOf('[');
        if (bracket < 0) return state.toLowerCase(java.util.Locale.ROOT);
        String name = state.substring(0, bracket).toLowerCase(java.util.Locale.ROOT);
        String values = state.substring(bracket + 1, state.length() - 1);
        List<String> properties = new ArrayList<String>();
        Collections.addAll(properties, values.split(","));
        Collections.sort(properties);
        StringBuilder out = new StringBuilder(name).append('[');
        for (int i = 0; i < properties.size(); i++) {
            if (i > 0) out.append(',');
            out.append(properties.get(i));
        }
        return out.append(']').toString();
    }
}
