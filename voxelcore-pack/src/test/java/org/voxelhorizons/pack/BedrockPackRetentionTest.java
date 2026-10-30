package org.voxelhorizons.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.Assert.*;

public class BedrockPackRetentionTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void keepsCurrentAndPreviousVerifiedPublication() throws Exception {
        Path output = temp.newFolder().toPath();
        Path first = revision(output, 1, 1000);
        Path previouslyPublished = revision(output, 2, 2000);
        Path newerButUnpublished = revision(output, 3, 3000);
        Path current = revision(output, 4, 4000);
        Path cache = Files.createDirectory(output.resolve("icon-cache"));
        Files.write(cache.resolve("render.png"), new byte[]{1, 2, 3});
        Files.write(output.resolve("glyph-tiles.json"), "{}".getBytes(StandardCharsets.UTF_8));

        assertEquals(2, BedrockPackRetention.prune(output, name(current), name(previouslyPublished), 2));
        assertFalse(Files.exists(first));
        assertTrue(Files.exists(previouslyPublished.resolve("voxelcore.mcpack")));
        assertFalse(Files.exists(newerButUnpublished));
        assertTrue(Files.exists(current.resolve("voxelcore-items.json")));
        assertTrue(Files.exists(cache.resolve("render.png")));
        assertTrue(Files.exists(output.resolve("glyph-tiles.json")));
        assertEquals("Repeated cleanup is idempotent", 0,
                BedrockPackRetention.prune(output, name(current), name(previouslyPublished), 2));
    }

    @Test public void exportOnlyRetainsNewestRevisionsPlusCurrent() throws Exception {
        Path output = temp.newFolder().toPath();
        Path current = revision(output, 1, 1000);
        Path old = revision(output, 2, 2000);
        Path recent = revision(output, 3, 3000);
        assertEquals(1, BedrockPackRetention.prune(output, name(current), null, 2));
        assertTrue(Files.exists(current));
        assertFalse(Files.exists(old));
        assertTrue(Files.exists(recent));
    }

    @Test public void disabledCleanupLeavesAllExportsUntouched() throws Exception {
        Path output = temp.newFolder().toPath();
        Path old = revision(output, 1, 1000);
        Path current = revision(output, 2, 2000);
        assertEquals(0, BedrockPackRetention.prune(output, name(current), null, 0));
        assertTrue(Files.exists(old));
    }

    @Test public void ignoresUnknownOrModifiedDirectories() throws Exception {
        Path output = temp.newFolder().toPath();
        Path old = revision(output, 1, 1000);
        Path current = revision(output, 2, 2000);
        Path modified = revision(output, 3, 3000);
        Files.write(modified.resolve("notes.txt"), "user data".getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(output.resolve("not-a-revision"));
        Path incomplete = output.resolve(hex(4));
        Files.createDirectory(incomplete);
        Files.write(incomplete.resolve("voxelcore.mcpack"), new byte[]{1});
        assertEquals(1, BedrockPackRetention.prune(output, name(current), null, 1));
        assertFalse(Files.exists(old));
        assertTrue(Files.exists(current));
        assertTrue(Files.exists(modified.resolve("notes.txt")));
        assertTrue(Files.exists(incomplete));
        assertTrue(Files.exists(output.resolve("not-a-revision")));
    }

    @Test public void refusesToPruneWithoutCompleteCurrentRevision() throws Exception {
        Path output = temp.newFolder().toPath();
        Path old = revision(output, 1, 1000);
        try {
            BedrockPackRetention.prune(output, hex(2), null, 1);
            fail("Missing current export must prevent cleanup");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Current Bedrock build"));
        }
        assertTrue(Files.exists(old));
    }

    private static Path revision(Path root, int id, long modified) throws IOException {
        Path directory = Files.createDirectory(root.resolve(hex(id)));
        Files.write(directory.resolve("voxelcore.mcpack"), new byte[]{1, 2, 3});
        Files.write(directory.resolve("voxelcore-items.json"), "{}".getBytes(StandardCharsets.UTF_8));
        Files.write(directory.resolve("unsupported-items.txt"), new byte[0]);
        Files.write(directory.resolve("unsupported-geometry.txt"), new byte[0]);
        Files.setLastModifiedTime(directory, FileTime.fromMillis(modified));
        return directory;
    }

    private static String name(Path directory) { return directory.getFileName().toString(); }
    private static String hex(int id) { return String.format("%064x", id); }
}
