package org.voxelhorizons.pack;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Finds and (opt-in) rewrites unmanaged literal glyph codepoints that collide with
 * Bedrock's reserved controller/emoji range across authored font JSON, DeluxeMenus
 * YAML, rank/tag text and similar files. Font definitions and their references must
 * move together, otherwise Java screens break; so a rewrite plans one codepoint map
 * and applies it to every scanned file, keeping each occurrence's literal or
 * {@code \\uXXXX} escape form. Originals are backed up once, never overwritten.
 */
public final class GlyphLiteralMigration {
    static final int RESERVED_FIRST = 0xE000, RESERVED_LAST = 0xE1FF;
    static final int TARGET_FIRST = 0xE200, TARGET_LAST = 0xF7FF;
    private static final long MAX_FILE_BYTES = 8_000_000L;
    private static final Set<String> EXTENSIONS = new TreeSet<String>(java.util.Arrays.asList(
            "json", "yml", "yaml", "txt", "properties", "mcfunction"));

    /** One reserved codepoint occurrence. */
    public static final class Finding {
        public final Path file;
        public final int line, column, codePoint;
        public final boolean escaped;
        Finding(Path file, int line, int column, int codePoint, boolean escaped) {
            this.file = file; this.line = line; this.column = column;
            this.codePoint = codePoint; this.escaped = escaped;
        }
        @Override public String toString() {
            return file + ":" + line + ":" + column + " " + codePoint(codePoint) + (escaped ? " (escape)" : " (literal)");
        }
    }

    public static final class Result {
        public final List<Finding> findings;
        public final Map<Integer, Integer> plan;
        public final List<Path> rewritten;
        Result(List<Finding> findings, Map<Integer, Integer> plan, List<Path> rewritten) {
            this.findings = Collections.unmodifiableList(findings);
            this.plan = Collections.unmodifiableMap(plan);
            this.rewritten = Collections.unmodifiableList(rewritten);
        }
    }

    private GlyphLiteralMigration() {}

    /** Codepoints already owned by VoxelCore content; pass as {@code used} to {@link #migrate}. */
    public static Set<Integer> occupied(Path contentRoot, Path glyphAllocationManifest) {
        return UiGlyphLoader.occupiedCodePoints(contentRoot, glyphAllocationManifest);
    }

    /** Report every reserved occurrence under the roots; never writes. */
    public static List<Finding> scan(List<Path> roots) throws IOException {
        List<Finding> findings = new ArrayList<Finding>();
        for (Path file : files(roots)) {
            String text = read(file);
            if (text != null) visit(text, file, null, findings);
        }
        return findings;
    }

    /**
     * Deterministic map from each reserved codepoint found to the lowest free target
     * codepoint not in {@code used} (managed allocations, tooltip/spacing glyphs and
     * any authored symbol already outside the reserved range).
     */
    public static Map<Integer, Integer> plan(List<Finding> findings, Set<Integer> used) {
        return plan(findings, used, Collections.<Integer, Integer>emptyMap());
    }

    /** As {@link #plan(List, Set)}, but earlier decisions win so late references follow the font. */
    static Map<Integer, Integer> plan(List<Finding> findings, Set<Integer> used, Map<Integer, Integer> prior) {
        Set<Integer> occupied = new TreeSet<Integer>(used);
        for (Finding finding : findings) occupied.add(Integer.valueOf(finding.codePoint));
        occupied.addAll(prior.values());
        Set<Integer> reserved = new TreeSet<Integer>();
        for (Finding finding : findings) reserved.add(Integer.valueOf(finding.codePoint));
        Map<Integer, Integer> plan = new TreeMap<Integer, Integer>(prior);
        int next = TARGET_FIRST;
        for (Integer old : reserved) {
            if (plan.containsKey(old)) continue;
            while (next <= TARGET_LAST && occupied.contains(Integer.valueOf(next))) next++;
            if (next > TARGET_LAST) throw new IllegalStateException("No private-use codepoints remain for " + codePoint(old));
            plan.put(old, Integer.valueOf(next));
            occupied.add(Integer.valueOf(next));
        }
        return plan;
    }

    /**
     * Scan, plan and rewrite every affected file. Backups go to {@code backupRoot}
     * mirroring each file's path relative to its scan root; an existing backup is
     * kept (it is the pre-migration original). All new contents are computed before
     * any file is replaced, and each replacement is atomic where supported.
     */
    public static Result migrate(List<Path> roots, Set<Integer> used, Path backupRoot) throws IOException {
        for (Path root : roots)
            if (backupRoot.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize()))
                throw new IOException("Glyph migration root " + root + " contains the backup folder " + backupRoot);
        Path planFile = backupRoot.resolve("glyph-literal-plan.txt");
        List<Finding> findings = scan(roots);
        Map<Integer, Integer> plan = plan(findings, used, readPlan(planFile));
        Map<Path, String> updates = new LinkedHashMap<Path, String>();
        Map<Path, Path> backups = new LinkedHashMap<Path, Path>();
        for (int index = 0; index < roots.size(); index++) {
            Path root = roots.get(index);
            String label = index + "-" + (root.getFileName() == null ? "root" : root.getFileName().toString());
            for (Path file : files(Collections.singletonList(root))) {
                if (updates.containsKey(file)) continue;
                String text = read(file);
                if (text == null) continue;
                StringBuilder out = new StringBuilder(text.length());
                if (!visit(text, file, new Rewrite(plan, out), null)) continue;
                updates.put(file, out.toString());
                backups.put(file, backupRoot.resolve(label).resolve(root.relativize(file)).normalize());
            }
        }
        for (Map.Entry<Path, Path> backup : backups.entrySet()) {
            Path target = backup.getValue();
            if (!target.startsWith(backupRoot.normalize())) throw new IOException("Unsafe backup path " + target);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) continue;
            Files.createDirectories(target.getParent());
            Files.copy(backup.getKey(), target, StandardCopyOption.COPY_ATTRIBUTES);
        }
        if (!plan.isEmpty()) writePlan(planFile, plan);
        List<Path> rewritten = new ArrayList<Path>();
        for (Map.Entry<Path, String> update : updates.entrySet()) {
            replace(update.getKey(), update.getValue());
            rewritten.add(update.getKey());
        }
        return new Result(findings, plan, rewritten);
    }

    /** Dry run: the plan {@link #migrate} would apply, without writing anything. */
    public static Result preview(List<Finding> findings, Set<Integer> used, Path backupRoot) throws IOException {
        return new Result(findings, plan(findings, used, readPlan(backupRoot.resolve("glyph-literal-plan.txt"))),
                Collections.<Path>emptyList());
    }

    /** Human-readable report for operators; stable ordering for diffs. */
    public static String report(Result result) {
        StringBuilder text = new StringBuilder("# Bedrock-reserved glyph literal migration\n");
        for (Map.Entry<Integer, Integer> entry : result.plan.entrySet())
            text.append(codePoint(entry.getKey())).append(" -> ").append(codePoint(entry.getValue())).append('\n');
        text.append("# occurrences\n");
        for (Finding finding : result.findings) text.append(finding).append('\n');
        text.append("# rewritten files\n");
        for (Path file : result.rewritten) text.append(file).append('\n');
        return text.toString();
    }

    static Map<Integer, Integer> readPlan(Path file) throws IOException {
        Map<Integer, Integer> plan = new TreeMap<Integer, Integer>();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return plan;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s*->\\s*");
            if (parts.length != 2 || !parts[0].startsWith("U+") || !parts[1].startsWith("U+"))
                throw new IOException("Invalid glyph migration plan line in " + file + ": " + line);
            int from = Integer.parseInt(parts[0].substring(2), 16), to = Integer.parseInt(parts[1].substring(2), 16);
            if (!reserved(from) || to < TARGET_FIRST || to > TARGET_LAST || plan.containsValue(Integer.valueOf(to)))
                throw new IOException("Unsafe glyph migration plan entry in " + file + ": " + line);
            plan.put(Integer.valueOf(from), Integer.valueOf(to));
        }
        return plan;
    }

    private static void writePlan(Path file, Map<Integer, Integer> plan) throws IOException {
        StringBuilder text = new StringBuilder("# Bedrock-reserved literal -> migrated codepoint. Reused by later runs.\n");
        for (Map.Entry<Integer, Integer> entry : plan.entrySet())
            text.append(codePoint(entry.getKey())).append(" -> ").append(codePoint(entry.getValue())).append('\n');
        Files.createDirectories(file.getParent());
        replace(file, text.toString());
    }

    private static final class Rewrite {
        final Map<Integer, Integer> plan; final StringBuilder out;
        Rewrite(Map<Integer, Integer> plan, StringBuilder out) { this.plan = plan; this.out = out; }
    }

    /** Walks literal and backslash-u escapes; returns true if any reserved codepoint was seen. */
    private static boolean visit(String text, Path file, Rewrite rewrite, List<Finding> findings) {
        boolean seen = false;
        int line = 1, column = 1;
        for (int i = 0; i < text.length(); ) {
            char c = text.charAt(i);
            // Only the 4-digit backslash-u form: YAML backslash-U takes 8 digits and never names U+E000..U+E1FF here.
            if (c == '\\' && i + 6 <= text.length() && text.charAt(i + 1) == 'u'
                    && !escapedBackslash(text, i) && hex(text, i + 2)) {
                int value = Integer.parseInt(text.substring(i + 2, i + 6), 16);
                if (reserved(value)) {
                    seen = true;
                    if (findings != null) findings.add(new Finding(file, line, column, value, true));
                    if (rewrite != null) {
                        String original = text.substring(i + 2, i + 6);
                        String digits = String.format(Locale.ROOT, "%04X", rewrite.plan.get(Integer.valueOf(value)));
                        boolean lower = !original.equals(original.toUpperCase(Locale.ROOT));
                        rewrite.out.append(text, i, i + 2).append(lower ? digits.toLowerCase(Locale.ROOT) : digits);
                    }
                    i += 6; column += 6;
                    continue;
                }
            }
            if (reserved(c)) {
                seen = true;
                if (findings != null) findings.add(new Finding(file, line, column, c, false));
                if (rewrite != null) rewrite.out.append((char) rewrite.plan.get(Integer.valueOf(c)).intValue());
            } else if (rewrite != null) {
                rewrite.out.append(c);
            }
            if (c == '\n') { line++; column = 1; } else column++;
            i++;
        }
        return seen;
    }

    /** {@code \\uE000} is an escaped backslash followed by text, not a codepoint escape. */
    private static boolean escapedBackslash(String text, int index) {
        int count = 0;
        for (int i = index - 1; i >= 0 && text.charAt(i) == '\\'; i--) count++;
        return count % 2 == 1;
    }

    private static boolean hex(String text, int start) {
        for (int i = start; i < start + 4; i++) if (Character.digit(text.charAt(i), 16) < 0) return false;
        return true;
    }

    private static boolean reserved(int codePoint) { return codePoint >= RESERVED_FIRST && codePoint <= RESERVED_LAST; }

    static String codePoint(int value) { return String.format(Locale.ROOT, "U+%04X", value); }

    private static List<Path> files(List<Path> roots) throws IOException {
        final List<Path> out = new ArrayList<Path>();
        for (Path root : roots) {
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) continue;
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    String name = file.getFileName().toString();
                    int dot = name.lastIndexOf('.');
                    // Symlinks are skipped (default walk does not follow them; regular-file check excludes them).
                    if (attributes.isRegularFile() && dot > 0 && attributes.size() <= MAX_FILE_BYTES
                            && EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT))) out.add(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        Collections.sort(out);
        return out;
    }

    /** Strict UTF-8; non-UTF-8 files are skipped rather than corrupted by a rewrite. */
    private static String read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException notUtf8) {
            return null;
        }
    }

    private static void replace(Path file, String text) throws IOException {
        Path staging = Files.createTempFile(file.getParent(), ".voxelcore-glyph-", ".tmp");
        try {
            Files.write(staging, text.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(staging, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staging, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staging);
        }
    }
}
