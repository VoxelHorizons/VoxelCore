package org.voxelhorizons.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

public final class GlyphLiteralMigrationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static void write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Path file) throws Exception {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    @Test public void scansLiteralAndEscapeFormsButNotEscapedBackslashes() throws Exception {
        Path root = temporary.newFolder("packs").toPath();
        write(root.resolve("font/default.json"), "{\"chars\":[\"\\ue025\"]}");
        write(root.resolve("menus/profile.yml"), "title: \"\uE025 Profile\"\nnote: '\\\\uE026 is text'\nok: \"\uE200\"\n");
        List<GlyphLiteralMigration.Finding> findings = GlyphLiteralMigration.scan(Collections.singletonList(root));
        assertEquals(2, findings.size());
        assertTrue(findings.get(0).escaped);
        assertEquals(0xE025, findings.get(0).codePoint);
        assertFalse(findings.get(1).escaped);
        assertEquals(1, findings.get(1).line);
        assertEquals(9, findings.get(1).column);
    }

    @Test public void planSkipsUsedCodepointsDeterministically() {
        Path file = temporary.getRoot().toPath().resolve("x.yml");
        List<GlyphLiteralMigration.Finding> findings = Arrays.asList(
                new GlyphLiteralMigration.Finding(file, 1, 1, 0xE100, false),
                new GlyphLiteralMigration.Finding(file, 1, 2, 0xE025, true),
                new GlyphLiteralMigration.Finding(file, 2, 1, 0xE025, false));
        java.util.Map<Integer, Integer> plan = GlyphLiteralMigration.plan(findings,
                new HashSet<Integer>(Arrays.asList(0xE200, 0xE202)));
        assertEquals(Integer.valueOf(0xE201), plan.get(0xE025));
        assertEquals(Integer.valueOf(0xE203), plan.get(0xE100));
        assertEquals(2, plan.size());
    }

    @Test public void rewritesFontAndReferencesTogetherWithImmutableBackup() throws Exception {
        Path root = temporary.newFolder("plugins").toPath();
        Path backups = temporary.getRoot().toPath().resolve("backup");
        Path font = root.resolve("VoxelCore/assets/minecraft/font/default.json");
        Path menu = root.resolve("DeluxeMenus/gui_menus/profile.yml");
        Path untouched = root.resolve("DeluxeMenus/config.yml");
        String fontText = "{\"providers\":[{\"chars\":[\"\\ue025\\uE026\"]}]}";
        String menuText = "menu_title: '&f\uE025\uE026'\n";
        write(font, fontText);
        write(menu, menuText);
        write(untouched, "plain: true\n");
        GlyphLiteralMigration.Result result = GlyphLiteralMigration.migrate(Collections.singletonList(root),
                new HashSet<Integer>(Collections.singleton(0xE200)), backups);
        assertEquals("{\"providers\":[{\"chars\":[\"\\ue201\\uE202\"]}]}", read(font));
        assertEquals("menu_title: '&f\uE201\uE202'\n", read(menu));
        assertEquals("plain: true\n", read(untouched));
        assertEquals(2, result.rewritten.size());
        Path fontBackup = backups.resolve("0-plugins/VoxelCore/assets/minecraft/font/default.json");
        assertEquals(fontText, read(fontBackup));
        assertTrue(GlyphLiteralMigration.report(result).contains("U+E025 -> U+E201"));

        // A later stray reference follows the original plan and the first backup is preserved.
        write(menu, "menu_title: '\uE026'\n");
        GlyphLiteralMigration.migrate(Collections.singletonList(root),
                new HashSet<Integer>(Arrays.asList(0xE200, 0xE201, 0xE202)), backups);
        assertEquals("menu_title: '\uE202'\n", read(menu));
        assertEquals(menuText, read(backups.resolve("0-plugins/DeluxeMenus/gui_menus/profile.yml")));
        assertTrue(GlyphLiteralMigration.scan(Collections.singletonList(root)).isEmpty());
    }

    @Test public void refusesRootThatWouldRewriteItsOwnBackups() throws Exception {
        Path root = temporary.newFolder("data").toPath();
        write(root.resolve("menu.yml"), "t: '\uE025'\n");
        try {
            GlyphLiteralMigration.migrate(Collections.singletonList(root), Collections.<Integer>emptySet(), root.resolve("backup"));
            fail("Backups inside a scan root must be refused");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("backup"));
        }
        assertEquals("t: '\uE025'\n", read(root.resolve("menu.yml")));
    }

    @Test public void leavesNonUtf8FilesUntouched() throws Exception {
        Path root = temporary.newFolder("binary").toPath();
        Path file = root.resolve("legacy.txt");
        byte[] latin1 = new byte[]{(byte) 0xEE, (byte) 0x80, 0x41, (byte) 0xFF};
        Files.write(file, latin1);
        GlyphLiteralMigration.Result result = GlyphLiteralMigration.migrate(Collections.singletonList(root),
                Collections.<Integer>emptySet(), temporary.getRoot().toPath().resolve("b"));
        assertTrue(result.rewritten.isEmpty());
        assertArrayEquals(latin1, Files.readAllBytes(file));
    }
}
