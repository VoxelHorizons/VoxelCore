package org.voxelhorizons.content.render;

import org.voxelhorizons.content.ContentID;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads and writes the stable schema-2 render allocation manifest. */
public final class RenderAllocationStore {
    public static final int SCHEMA = 2;

    private final Path path;

    public RenderAllocationStore(Path path) {
        if (path == null) throw new IllegalArgumentException("path cannot be null");
        this.path = path;
    }

    public Path path() { return path; }

    public RenderAllocationRegistry load() {
        if (!Files.exists(path)) return RenderAllocationRegistry.empty();
        Object loaded;
        try (InputStream input = Files.newInputStream(path)) {
            loaded = new Yaml().load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read render allocation manifest " + path, exception);
        }
        if (!(loaded instanceof Map)) throw new IllegalStateException("Render allocation manifest must be a mapping: " + path);
        Map<?, ?> root = (Map<?, ?>) loaded;
        Object schema = root.get("schema");
        if (!(schema instanceof Number) || ((Number) schema).intValue() != SCHEMA) {
            throw new IllegalStateException("Unsupported render allocation manifest schema in " + path + "; expected " + SCHEMA);
        }

        int nextValue = RenderAllocationRegistry.FIRST_AUTO_CUSTOM_MODEL_DATA;
        Object next = root.get("next_custom_model_data");
        if (next instanceof Number) nextValue = Math.max(nextValue, ((Number) next).intValue());

        Map<ContentID, RenderAllocation> allocations = new LinkedHashMap<ContentID, RenderAllocation>();
        Object values = root.get("allocations");
        if (values instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) values).entrySet()) {
                if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Map)) continue;
                ContentID id = ContentID.parse((String) entry.getKey(), "voxelhorizons");
                Map<?, ?> value = (Map<?, ?>) entry.getValue();
                Object cmd = value.get("custom_model_data");
                Object model = value.get("model");
                Object active = value.get("active");
                if (!(cmd instanceof Number) || !(model instanceof String)) {
                    throw new IllegalStateException("Invalid render allocation entry for " + id + " in " + path);
                }
                allocations.put(id, new RenderAllocation(((Number) cmd).intValue(), (String) model,
                        !(active instanceof Boolean) || ((Boolean) active).booleanValue()));
            }
        }
        return new RenderAllocationRegistry(nextValue, allocations);
    }

    public void save(RenderAllocationRegistry registry) {
        StringBuilder out = new StringBuilder();
        out.append("schema: ").append(SCHEMA).append('\n');
        out.append("next_custom_model_data: ").append(registry.nextCustomModelData()).append('\n');
        out.append("allocations:\n");

        List<Map.Entry<ContentID, RenderAllocation>> entries = new ArrayList<Map.Entry<ContentID, RenderAllocation>>(registry.entries().entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<ContentID, RenderAllocation>>() {
            @Override public int compare(Map.Entry<ContentID, RenderAllocation> left, Map.Entry<ContentID, RenderAllocation> right) {
                return left.getKey().toString().compareTo(right.getKey().toString());
            }
        });
        for (Map.Entry<ContentID, RenderAllocation> entry : entries) {
            RenderAllocation allocation = entry.getValue();
            out.append("  '").append(yaml(entry.getKey().toString())).append("':\n");
            out.append("    custom_model_data: ").append(allocation.customModelData()).append('\n');
            out.append("    model: '").append(yaml(allocation.model())).append("'\n");
            out.append("    active: ").append(allocation.active()).append('\n');
        }

        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            Files.write(temp, out.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write render allocation manifest " + path, exception);
        }
    }

    private static String yaml(String value) { return value.replace("'", "''"); }
}
