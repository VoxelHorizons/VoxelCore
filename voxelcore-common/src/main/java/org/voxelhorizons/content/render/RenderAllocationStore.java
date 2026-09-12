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

/** Reads schema-2 manifests and writes schema-3 render allocations with stable structured indices. */
public final class RenderAllocationStore {
    public static final int SCHEMA = 3;

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
        Object schemaObject = root.get("schema");
        if (!(schemaObject instanceof Number)) {
            throw new IllegalStateException("Render allocation manifest schema is missing in " + path);
        }
        int schema = ((Number) schemaObject).intValue();
        if (schema != 2 && schema != SCHEMA) {
            throw new IllegalStateException("Unsupported render allocation manifest schema " + schema + " in " + path
                    + "; expected 2 or " + SCHEMA);
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
                allocations.put(id, new RenderAllocation(((Number) cmd).intValue(), normalizeModel((String) model),
                        !(active instanceof Boolean) || ((Boolean) active).booleanValue()));
            }
        }

        Map<String, StructuredModelDataAllocation> structured = new LinkedHashMap<String, StructuredModelDataAllocation>();
        if (schema >= 3) {
            Object models = root.get("structured_model_data");
            if (models instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) models).entrySet()) {
                    if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Map)) continue;
                    String model = normalizeModel((String) entry.getKey());
                    Map<?, ?> value = (Map<?, ?>) entry.getValue();
                    structured.put(model, new StructuredModelDataAllocation(
                            parseIndices(value.get("floats"), model, "floats"),
                            parseIndices(value.get("flags"), model, "flags"),
                            parseIndices(value.get("strings"), model, "strings"),
                            parseIndices(value.get("colors"), model, "colors")));
                }
            }
        }

        Map<ContentID, RenderAllocation> enriched = new LinkedHashMap<ContentID, RenderAllocation>();
        for (Map.Entry<ContentID, RenderAllocation> entry : allocations.entrySet()) {
            StructuredModelDataAllocation modelAllocation = structured.get(entry.getValue().model());
            enriched.put(entry.getKey(), entry.getValue().withStructuredModelData(
                    modelAllocation == null ? StructuredModelDataAllocation.empty() : modelAllocation));
        }
        return new RenderAllocationRegistry(nextValue, enriched, structured);
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

        out.append("structured_model_data:\n");
        List<String> models = new ArrayList<String>(registry.structuredModels().keySet());
        Collections.sort(models);
        for (String model : models) {
            StructuredModelDataAllocation allocation = registry.structuredModels().get(model);
            out.append("  '").append(yaml(model)).append("':\n");
            writeIndices(out, "floats", allocation.floats(), 4);
            writeIndices(out, "flags", allocation.flags(), 4);
            writeIndices(out, "strings", allocation.strings(), 4);
            writeIndices(out, "colors", allocation.colors(), 4);
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

    private static Map<String, Integer> parseIndices(Object raw, String model, String type) {
        Map<String, Integer> result = new LinkedHashMap<String, Integer>();
        if (raw == null) return result;
        if (!(raw instanceof Map)) throw new IllegalStateException(type + " must be a mapping for structured model " + model);
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Number)) {
                throw new IllegalStateException("Invalid " + type + " structured allocation for model " + model);
            }
            int index = ((Number) entry.getValue()).intValue();
            if (index < 0) throw new IllegalStateException(type + " index cannot be negative for model " + model);
            result.put((String) entry.getKey(), Integer.valueOf(index));
        }
        return result;
    }

    private static void writeIndices(StringBuilder out, String name, Map<String, Integer> values, int indent) {
        String prefix = spaces(indent);
        out.append(prefix).append(name).append(":\n");
        List<String> keys = new ArrayList<String>(values.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            out.append(prefix).append("  '").append(yaml(key)).append("': ").append(values.get(key)).append('\n');
        }
    }

    private static String normalizeModel(String value) {
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String spaces(int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) result.append(' ');
        return result.toString();
    }

    private static String yaml(String value) { return value.replace("'", "''"); }
}
