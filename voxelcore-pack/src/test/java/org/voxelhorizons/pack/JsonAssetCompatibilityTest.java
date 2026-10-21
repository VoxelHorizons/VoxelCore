package org.voxelhorizons.pack;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JsonAssetCompatibilityTest {
    @Test
    public void mergesDisjointLanguageKeysAndKeepsEqualKeysOnce() {
        String merged = text(JsonAssetCompatibility.merge(bytes("{\"menu.play\":\"Play\",\"menu.quit\":\"Quit\"}"),
                bytes("{\"menu.play\":\"Play\",\"menu.return\":\"Return\"}"),
                "assets/minecraft/lang/af_za.json"));

        assertTrue(merged.contains("\"menu.play\":\"Play\""));
        assertTrue(merged.contains("\"menu.quit\":\"Quit\""));
        assertTrue(merged.contains("\"menu.return\":\"Return\""));
        assertTrue(merged.indexOf("menu.play") == merged.lastIndexOf("menu.play"));
    }

    @Test
    public void recursivelyMergesObjectsAndPreservesArrayEntries() {
        String merged = text(JsonAssetCompatibility.merge(
                bytes("{\"animation\":{\"frametime\":2,\"frames\":[0,1]}}"),
                bytes("{\"animation\":{\"frames\":[1,2],\"interpolate\":true}}"),
                "assets/example/textures/block/test.png.mcmeta"));

        assertTrue(merged.contains("\"frametime\":2"));
        assertTrue(merged.contains("\"interpolate\":true"));
        assertTrue(merged.contains("\"frames\":[0,1,1,2]"));
    }

    @Test
    public void reportsTheConflictingJsonKeyWhenValuesCannotBeCombined() {
        try {
            JsonAssetCompatibility.merge(bytes("{\"menu.play\":\"Play\"}"),
                    bytes("{\"menu.play\":\"Start\"}"), "assets/minecraft/lang/af_za.json");
            fail("Expected incompatible duplicate translation key to fail");
        } catch (JavaPackCompileException expected) {
            assertTrue(expected.getMessage().contains("assets/minecraft/lang/af_za.json"));
            assertTrue(expected.getMessage().contains("$.menu.play"));
        }
    }

    @Test
    public void rejectsDuplicateKeysInsideAnInputJsonObject() {
        try {
            JsonAssetCompatibility.merge(bytes("{\"menu.play\":\"Play\",\"menu.play\":\"Start\"}"),
                    bytes("{}"), "assets/minecraft/lang/af_za.json");
            fail("Expected duplicate input key to fail");
        } catch (JavaPackCompileException expected) {
            assertTrue(expected.getMessage().contains("duplicate key $.menu.play"));
        }
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String text(byte[] value) { return new String(value, StandardCharsets.UTF_8); }
}
