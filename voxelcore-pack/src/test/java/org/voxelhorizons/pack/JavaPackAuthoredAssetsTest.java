package org.voxelhorizons.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JavaPackAuthoredAssetsTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void copiesAllAuthoredAssetsIncludingVanillaOverridesAndUnreferencedFiles() throws Exception {
        File contentRoot = temporaryFolder.newFolder("content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());

        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        write(new File(pack, "assets/voxel/textures/misc/unreferenced.png"), "voxel-unreferenced");
        write(new File(pack, "assets/minecraft/textures/block/stone.png"), "vanilla-override");
        write(new File(pack, "assets/example/textures/gui/freeform.png"), "other-namespace");

        Path build = temporaryFolder.newFolder("build").toPath();
        Path output = build.resolve("pack.zip");
        JavaPackBuildResult result = new JavaPackCompiler().compile(
                contentRoot.toPath(), output, build.resolve("render-allocations.yml"),
                JavaPackTarget.numericCmd("asset-copy-test", 22));

        assertEquals(3, result.copiedAssets());
        assertEquals("voxel-unreferenced", zipText(output, "assets/voxel/textures/misc/unreferenced.png"));
        assertEquals("vanilla-override", zipText(output, "assets/minecraft/textures/block/stone.png"));
        assertEquals("other-namespace", zipText(output, "assets/example/textures/gui/freeform.png"));
    }

    @Test
    public void validationAcceptsAssetsOutsideTheContentPackNamespace() throws Exception {
        File contentRoot = temporaryFolder.newFolder("validate-content");
        File pack = new File(contentRoot, "voxel");
        assertTrue(new File(pack, "content").mkdirs());

        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        write(new File(pack, "assets/minecraft/textures/gui/container/generic_54.png"), "gui-override");

        Path build = temporaryFolder.newFolder("validate-build").toPath();
        JavaPackBuildResult result = new JavaPackCompiler().validate(
                contentRoot.toPath(), build.resolve("render-allocations.yml"),
                JavaPackTarget.numericCmd("asset-validation-test", 22));

        assertEquals(1, result.copiedAssets());
    }

    private static void write(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) assertTrue(parent.mkdirs());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static String zipText(Path zip, String name) throws Exception {
        ZipFile file = new ZipFile(zip.toFile());
        try {
            ZipEntry entry = file.getEntry(name);
            if (entry == null) throw new AssertionError("Missing zip entry " + name);
            InputStream input = file.getInputStream(entry);
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int read;
                while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            } finally {
                input.close();
            }
        } finally {
            file.close();
        }
    }
}
