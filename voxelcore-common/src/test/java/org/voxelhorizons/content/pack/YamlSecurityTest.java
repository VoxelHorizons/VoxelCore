package org.voxelhorizons.content.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.voxelhorizons.content.block.BlockAllocationStore;
import org.voxelhorizons.content.render.RenderAllocationStore;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** All VoxelCore-authored and saved YAML must use a restricted constructor. */
public final class YamlSecurityTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void rejectsJavaClassTags() {
        expectRejected(new Runnable() {
            @Override public void run() {
                ContentPackDiscovery.yaml().load("!!java.lang.String tagged");
            }
        });
    }

    @Test
    public void rejectsDuplicateKeys() {
        expectRejected(new Runnable() {
            @Override public void run() {
                ContentPackDiscovery.yaml().load("schema: 1\\nschema: 2\\n".replace("\\n", "\n"));
            }
        });
    }

    @Test
    public void acceptsPlainYamlMaps() {
        Object value = ContentPackDiscovery.yaml().load("schema: 1\nnamespace: example\n");
        assertEquals(1, ((java.util.Map<?, ?>) value).get("schema"));
    }

    @Test
    public void blockAllocationsRejectUnsafeTags() throws Exception {
        final File file = folder.newFile("block-allocations.yml");
        Files.write(file.toPath(), ("schema: 1\nallocations:\n  example: !!java.lang.String malicious\n")
                .getBytes(StandardCharsets.UTF_8));
        expectRejected(new Runnable() {
            @Override public void run() {
                new BlockAllocationStore(file.toPath()).load();
            }
        });
    }

    @Test
    public void renderAllocationsRejectUnsafeTags() throws Exception {
        final File file = folder.newFile("render-allocations.yml");
        Files.write(file.toPath(), ("schema: 3\nallocations:\n  example: !!java.lang.String malicious\n")
                .getBytes(StandardCharsets.UTF_8));
        expectRejected(new Runnable() {
            @Override public void run() {
                new RenderAllocationStore(file.toPath()).load();
            }
        });
    }

    private static void expectRejected(Runnable action) {
        try {
            action.run();
            fail("Unsafe or ambiguous YAML was accepted");
        } catch (YAMLException expected) {
            // Restricted SnakeYAML constructor blocks Java class tags and duplicate keys.
        }
    }
}
