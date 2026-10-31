package org.voxelhorizons.pack;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prunes older local Bedrock build exports after successful publication.
 * Never touches the icon cache, non-revision folders, symlinks or directories
 * containing unrecognised files. Publication destinations are managed separately.
 */
public final class BedrockPackRetention {
    private static final Set<String> GENERATED_FILES = new HashSet<>(Arrays.asList(
            "voxelcore.mcpack", "voxelcore-items.json", "voxelcore-blocks.json",
            "unsupported-items.txt", "unsupported-geometry.txt"));

    private BedrockPackRetention() { }

    /**
     * Keep the current build, the last verified published revision (when available),
     * and the most recently written generated revisions up to {@code keepRevisions}.
     * A zero or negative limit disables pruning. Protected revisions can exceed
     * the limit, rather than making rollback impossible.
     *
     * @return number of revision directories removed
     */
    public static int prune(Path outputRoot, String currentRevision, String previousPublishedRevision,
                            int keepRevisions) throws IOException {
        if (keepRevisions <= 0) return 0;
        if (!isRevision(currentRevision)) throw new IOException("Invalid current Bedrock revision");
        if (Files.isSymbolicLink(outputRoot))
            throw new IOException("Refusing to prune a symbolic link: " + outputRoot);
        if (!Files.isDirectory(outputRoot, LinkOption.NOFOLLOW_LINKS)) return 0;

        List<Path> revisions = new ArrayList<>();
        Map<Path, FileTime> modified = new HashMap<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(outputRoot)) {
            for (Path entry : entries) {
                if (!isRevision(entry.getFileName().toString())
                        || !Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)
                        || !isGeneratedDirectory(entry)) continue;
                revisions.add(entry);
                modified.put(entry, Files.getLastModifiedTime(entry, LinkOption.NOFOLLOW_LINKS));
            }
        }

        Path current = outputRoot.resolve(currentRevision);
        if (!revisions.contains(current))
            throw new IOException("Current Bedrock build is not a complete generated revision: " + current);

        revisions.sort(Comparator.<Path, FileTime>comparing(modified::get).reversed()
                .thenComparing(entry -> entry.getFileName().toString()));
        Set<Path> keep = new HashSet<>();
        keep.add(current);
        if (isRevision(previousPublishedRevision)) {
            Path previous = outputRoot.resolve(previousPublishedRevision);
            if (revisions.contains(previous)) keep.add(previous);
        }
        int required = Math.max(keepRevisions, keep.size());
        for (Path revision : revisions) {
            if (keep.size() >= required) break;
            keep.add(revision);
        }

        int removed = 0;
        for (Path revision : revisions) {
            if (keep.contains(revision)) continue;
            // Recheck after enumeration. Only remove files created by VoxelCore,
            // and let Files.delete(directory) fail if any unexpected file appeared.
            if (!isGeneratedDirectory(revision)) continue;
            for (String filename : GENERATED_FILES) Files.deleteIfExists(revision.resolve(filename));
            Files.delete(revision);
            removed++;
        }
        return removed;
    }

    private static boolean isRevision(String name) {
        return name != null && name.matches("[0-9a-f]{64}");
    }

    private static boolean isGeneratedDirectory(Path directory) throws IOException {
        if (!Files.isRegularFile(directory.resolve("voxelcore.mcpack"), LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(directory.resolve("voxelcore-items.json"), LinkOption.NOFOLLOW_LINKS))
            return false;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                if (!GENERATED_FILES.contains(file.getFileName().toString())
                        || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return false;
            }
        }
        return true;
    }
}
