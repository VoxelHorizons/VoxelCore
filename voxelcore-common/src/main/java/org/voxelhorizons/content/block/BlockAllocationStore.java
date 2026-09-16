package org.voxelhorizons.content.block;

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

/** Atomic persistence for stable custom-block carrier allocations. */
public final class BlockAllocationStore {
    public static final int SCHEMA = 1;
    private final Path path;

    public BlockAllocationStore(Path path) { this.path = path; }
    public Path path() { return path; }

    public BlockAllocationRegistry load() {
        if (!Files.exists(path)) return BlockAllocationRegistry.empty();
        Object loaded;
        try (InputStream input = Files.newInputStream(path)) { loaded = new Yaml().load(input); }
        catch (IOException exception) { throw new IllegalStateException("Unable to read block allocations " + path, exception); }
        if (!(loaded instanceof Map)) throw new IllegalStateException("Block allocation manifest must be a mapping: " + path);
        Map<?, ?> root = (Map<?, ?>) loaded;
        if (!(root.get("schema") instanceof Number) || ((Number) root.get("schema")).intValue() != SCHEMA) {
            throw new IllegalStateException("Unsupported block allocation schema in " + path);
        }
        Map<ContentID, BlockAllocation> values = new LinkedHashMap<ContentID, BlockAllocation>();
        Object raw = root.get("allocations");
        if (raw instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
                if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Map)) {
                    throw new IllegalStateException("Invalid block allocation entry in " + path);
                }
                Map<?, ?> value = (Map<?, ?>) entry.getValue();
                if (!(value.get("method") instanceof String) || !(value.get("slot") instanceof Number)) {
                    throw new IllegalStateException("Invalid block allocation for " + entry.getKey() + " in " + path);
                }
                BlockMethod method = BlockMethod.parse((String) value.get("method"));
                boolean active = !(value.get("active") instanceof Boolean) || ((Boolean) value.get("active")).booleanValue();
                values.put(ContentID.parse((String) entry.getKey(), "voxelhorizons"),
                        new BlockAllocation(method, ((Number) value.get("slot")).intValue(), active));
            }
        }
        return new BlockAllocationRegistry(values);
    }

    public void save(BlockAllocationRegistry registry) {
        StringBuilder out = new StringBuilder("schema: 1\nallocations:\n");
        List<Map.Entry<ContentID, BlockAllocation>> entries =
                new ArrayList<Map.Entry<ContentID, BlockAllocation>>(registry.entries().entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<ContentID, BlockAllocation>>() {
            @Override public int compare(Map.Entry<ContentID, BlockAllocation> left, Map.Entry<ContentID, BlockAllocation> right) {
                return left.getKey().toString().compareTo(right.getKey().toString());
            }
        });
        for (Map.Entry<ContentID, BlockAllocation> entry : entries) {
            out.append("  '").append(entry.getKey()).append("':\n");
            out.append("    method: ").append(entry.getValue().method().name().toLowerCase(java.util.Locale.ROOT)).append('\n');
            out.append("    slot: ").append(entry.getValue().slot()).append('\n');
            out.append("    active: ").append(entry.getValue().active()).append('\n');
        }
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            Files.write(temp, out.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write block allocations " + path, exception);
        }
    }
}
