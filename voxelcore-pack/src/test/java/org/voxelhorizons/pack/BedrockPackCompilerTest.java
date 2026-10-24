package org.voxelhorizons.pack;

import com.google.gson.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

public class BedrockPackCompilerTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Path fixture() throws Exception {
        Path root = temp.newFolder().toPath();
        write(root.resolve("demo/pack.yml"), "schema: 1\nnamespace: demo\n");
        write(root.resolve("demo/content/items.yml"), "items:\n  token:\n    material: minecraft:paper\n    render:\n      model: demo:item/token\n    properties:\n      bedrock:\n        texture: demo:item/token\n  unsupported:\n    material: minecraft:paper\n    render:\n      model: demo:item/other\n");
        Path texture = root.resolve("demo/assets/demo/textures/item/token.png");
        Files.createDirectories(texture.getParent());
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(16,16,2), "png", texture.toFile());
        return root;
    }
    @Test public void compilesMatchingPackAndMappingsDeterministically() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        BedrockPackCompiler compiler = new BedrockPackCompiler();
        BedrockPackCompiler.Result first = compiler.compile(root,out), second = compiler.compile(root,out);
        assertEquals(first.revision, second.revision); assertEquals(1, first.mappedItems); assertEquals(1, first.unsupportedItems);
        JsonObject mappings = JsonParser.parseString(new String(Files.readAllBytes(first.directory.resolve("voxelcore-items.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject item = mappings.getAsJsonObject("items").getAsJsonArray("minecraft:paper").get(0).getAsJsonObject();
        assertEquals("demo:item/token", item.get("model").getAsString());
        try (ZipFile zip = new ZipFile(first.directory.resolve("voxelcore.mcpack").toFile())) {
            assertNotNull(zip.getEntry("manifest.json")); assertNotNull(zip.getEntry("textures/item_texture.json"));
            assertEquals(4, zip.size());
        }
        Path geyser = temp.newFolder().toPath();
        write(geyser.resolve("packs/other.mcpack"), "unrelated");
        new GeyserPackPublisher().publish(first, geyser);
        assertArrayEquals(Files.readAllBytes(first.directory.resolve("voxelcore.mcpack")),Files.readAllBytes(geyser.resolve("packs/voxelcore.mcpack")));
        assertTrue(Files.exists(geyser.resolve("packs/other.mcpack")));
    }
    @Test public void vanillaTexturesFromClientJarRenderFurnitureIcons() throws Exception {
        Path root = temp.newFolder().toPath(), out = temp.newFolder().toPath();
        write(root.resolve("demo/pack.yml"), "schema: 1\nnamespace: demo\n");
        write(root.resolve("demo/content/items.yml"), "items:\n  bench:\n    material: minecraft:paper\n    render:\n      model: demo:item/bench\n");
        write(root.resolve("demo/assets/demo/models/item/bench.json"), "{\"textures\":{\"all\":\"minecraft:block/oak_planks\"},"
                + "\"elements\":[{\"from\":[0,0,0],\"to\":[16,8,16],\"faces\":{\"north\":{\"texture\":\"#all\"},\"up\":{\"texture\":\"#all\"}}}]}");
        BedrockPackCompiler.Result without = null;
        try { without = new BedrockPackCompiler().compile(root, out); } catch (java.io.IOException nothingMapped) { }
        assertTrue("A vanilla texture is not in the content pack", without == null || without.mappedItems == 0);
        Path jar = temp.newFile("client.jar").toPath();
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        java.awt.image.BufferedImage planks = new java.awt.image.BufferedImage(16,16,2);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) planks.setRGB(x, y, 0xffa0784a);
        javax.imageio.ImageIO.write(planks, "png", png);
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("assets/minecraft/textures/block/oak_planks.png")); zip.write(png.toByteArray());
            zip.putNextEntry(new java.util.zip.ZipEntry("net/minecraft/client/Main.class")); zip.write(new byte[]{1});
            zip.putNextEntry(new java.util.zip.ZipEntry("assets/minecraft/lang/en_us.json")); zip.write("{}".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals("Only models and textures are kept", 1, VanillaAssets.load(jar).size());
        BedrockPackCompiler.Result with = new BedrockPackCompiler().withVanillaAssets(jar).compile(root, out);
        assertEquals(1, with.mappedItems);
        try (ZipFile pack = new ZipFile(with.directory.resolve("voxelcore.mcpack").toFile())) {
            assertNull("Vanilla files are inputs, not shipped", pack.getEntry("textures/block/oak_planks.png"));
        }
    }
    @Test public void missingTextureDoesNotReplacePreviousRevision() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        BedrockPackCompiler compiler = new BedrockPackCompiler();
        BedrockPackCompiler.Result first = compiler.compile(root,out);
        byte[] original = Files.readAllBytes(first.directory.resolve("voxelcore.mcpack"));
        Files.delete(root.resolve("demo/assets/demo/textures/item/token.png"));
        try { compiler.compile(root,out); fail("Missing texture must fail"); }
        // The broken item is skipped (reported); with nothing left to map the build still fails.
        catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("No Bedrock assets or mapped items")); }
        assertArrayEquals(original, Files.readAllBytes(first.directory.resolve("voxelcore.mcpack")));
    }
    @Test public void textureChangeCreatesNewRevisionAndKeepsPrevious() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        BedrockPackCompiler compiler = new BedrockPackCompiler();
        BedrockPackCompiler.Result first = compiler.compile(root,out);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(16,16,2); image.setRGB(0,0,0xffff0000);
        javax.imageio.ImageIO.write(image,"png",root.resolve("demo/assets/demo/textures/item/token.png").toFile());
        BedrockPackCompiler.Result second = compiler.compile(root,out);
        assertNotEquals(first.revision,second.revision); assertTrue(Files.exists(first.directory.resolve("voxelcore.mcpack")));
    }
    @Test public void rejectsGeneratedAssetOverrides() throws Exception {
        Path root = fixture(); write(root.resolve("demo/bedrock/resource_pack/manifest.json"), "{}");
        try { new BedrockPackCompiler().compile(root,temp.newFolder().toPath()); fail(); }
        catch(java.io.IOException expected) { assertTrue(expected.getMessage().contains("generated")); }
    }
    @Test public void generatedLongItemIdentifiersUseShortUniqueValidBedrockPaths() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        Path definitions = root.resolve("demo/content/items.yml");
        String prefix = new String(Files.readAllBytes(definitions), StandardCharsets.UTF_8);
        String[] names = {
                "furniture_stripped_dark_oak_chair_corner_outer_left_variant",
                "furniture_stripped_dark_oak_log_wall_shelf_single_extended"
        };
        for (int i = 0; i < names.length; i++) {
            prefix += "  " + names[i] + ":\n    material: minecraft:paper\n"
                    + "    render:\n      model: demo:item/model" + i + "\n"
                    + "    properties:\n      bedrock:\n        texture: demo:item/token\n";
        }
        write(definitions, prefix);
        BedrockPackCompiler.Result built = new BedrockPackCompiler().compile(root, out);
        assertEquals(3, built.mappedItems);
        JsonObject mappings = JsonParser.parseString(new String(
                Files.readAllBytes(built.directory.resolve("voxelcore-items.json")), StandardCharsets.UTF_8))
                .getAsJsonObject();
        JsonObject atlas;
        java.util.Set<String> identifiers = new java.util.HashSet<String>();
        try (ZipFile zip = new ZipFile(built.directory.resolve("voxelcore.mcpack").toFile())) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> files = zip.entries();
            while (files.hasMoreElements()) {
                String path = files.nextElement().getName();
                assertTrue("Bedrock has >=80 byte path: " + path,
                        path.getBytes(StandardCharsets.UTF_8).length < 80);
            }
            java.util.zip.ZipEntry entry = zip.getEntry("textures/item_texture.json");
            assertNotNull(entry);
            try (java.io.InputStream input = zip.getInputStream(entry)) {
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[4096]; int n;
                while ((n = input.read(chunk)) != -1) buffer.write(chunk, 0, n);
                atlas = JsonParser.parseString(new String(buffer.toByteArray(), StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("texture_data");
            }
            for (JsonElement element : mappings.getAsJsonObject("items").getAsJsonArray("minecraft:paper")) {
                JsonObject def = element.getAsJsonObject();
                String id = def.get("bedrock_identifier").getAsString();
                assertTrue("duplicate identifier " + id, identifiers.add(id));
                assertTrue(id.length() <= 48);
                String icon = def.getAsJsonObject("bedrock_options").get("icon").getAsString();
                String texturePath = atlas.getAsJsonObject(icon).get("textures").getAsString() + ".png";
                assertNotNull("Missing generated texture: " + texturePath, zip.getEntry(texturePath));
            }
        }
        assertEquals(3, identifiers.size());
        assertEquals(built.revision, new BedrockPackCompiler().compile(root, out).revision);
    }
    @Test public void rejectsLongAuthoredBedrockPathsRatherThanSilentlyBreakingReferences() throws Exception {
        Path root = fixture();
        String longName = new String(new char[75]).replace('\0', 'x');
        String path = "ui/" + longName + ".json";
        write(root.resolve("demo/bedrock/resource_pack/" + path), "{}");
        try {
            new BedrockPackCompiler().compile(root, temp.newFolder().toPath());
            fail("Long authored file path must be rejected");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("maximum 79"));
            assertTrue(expected.getMessage().contains(path));
        }
    }
    @Test public void automaticallyPublishesToLoadedLocalGeyserDirectory() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        Path actualGeyser = temp.newFolder("Geyser-Spigot").toPath();
        GeyserPackPublisher.Destination destination =
                GeyserPackPublisher.resolveDestination("", true, actualGeyser);
        assertEquals(actualGeyser.toAbsolutePath(), destination.root);
        assertEquals("detected local plugin", destination.source);
        BedrockPackCompiler.Result compiled = new BedrockPackCompiler().compile(root, out);
        new GeyserPackPublisher().publish(compiled, destination.root);
        assertArrayEquals(Files.readAllBytes(compiled.directory.resolve("voxelcore.mcpack")),
                Files.readAllBytes(actualGeyser.resolve("packs/voxelcore.mcpack")));
        assertArrayEquals(Files.readAllBytes(compiled.directory.resolve("voxelcore-items.json")),
                Files.readAllBytes(actualGeyser.resolve("custom_mappings/voxelcore-items.json")));
        assertFalse(Files.exists(actualGeyser.resolve("extensions/voxelcore.jar")));
    }
    @Test public void explicitGeyserDirectoryOverridesLocalDetection() throws Exception {
        Path detected = temp.newFolder("local-geyser").toPath();
        Path configured = temp.newFolder("proxy-mounted-geyser").toPath();
        GeyserPackPublisher.Destination destination =
                GeyserPackPublisher.resolveDestination(configured.toString(), false, detected);
        assertEquals(configured.toAbsolutePath(), destination.root);
        assertEquals("configured path", destination.source);
    }
    @Test public void missingGeyserOrAutoPublishDisabledExportsOnly() throws Exception {
        GeyserPackPublisher.Destination absent = GeyserPackPublisher.resolveDestination("", true, null);
        assertNull(absent.root);
        assertTrue(absent.explanation.contains("proxy"));
        GeyserPackPublisher.Destination disabled =
                GeyserPackPublisher.resolveDestination("", false, temp.newFolder().toPath());
        assertNull(disabled.root);
        assertTrue(disabled.explanation.contains("auto_publish_local"));
    }
    @Test public void requiresRealGeyserDirectory() throws Exception {
        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(fixture(),temp.newFolder().toPath());
        try { new GeyserPackPublisher().publish(result,temp.getRoot().toPath().resolve("missing")); fail(); }
        catch(java.io.IOException expected) { assertTrue(expected.getMessage().contains("does not exist")); }
    }
    @Test public void sharesIdenticalSelectors() throws Exception {
        Path root = fixture();
        Path items = root.resolve("demo/content/items.yml");
        String content = new String(Files.readAllBytes(items), StandardCharsets.UTF_8);
        content += "  duplicate:\n    extends: token\n";
        Files.write(items, content.getBytes(StandardCharsets.UTF_8));
        assertEquals(1,new BedrockPackCompiler().compile(root,temp.newFolder().toPath()).mappedItems);
    }
    @Test public void conflictingIconsForOneJavaModelAreReportedNotFatal() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        Path items = root.resolve("demo/content/items.yml");
        String content = new String(Files.readAllBytes(items), StandardCharsets.UTF_8);
        content += "  twin:\n    material: minecraft:paper\n    render:\n      model: demo:item/token\n"
                + "    properties:\n      bedrock:\n        texture: demo:item/twin\n";
        Files.write(items, content.getBytes(StandardCharsets.UTF_8));
        Path twin = root.resolve("demo/assets/demo/textures/item/twin.png");
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(16,16,2);
        image.setRGB(1,1,0xff123456);
        javax.imageio.ImageIO.write(image, "png", twin.toFile());
        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(root, out);
        assertEquals("Only one Geyser definition per Java model", 1, result.mappedItems);
        String report = new String(Files.readAllBytes(result.directory.resolve("unsupported-items.txt")), StandardCharsets.UTF_8);
        assertTrue(report, report.contains("shares Java item minecraft:paper / demo:item/token"));
    }

    @Test public void authoredAssetsAreIncludedAndRemovedOnRebuild() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        Path asset = root.resolve("demo/bedrock/resource_pack/texts/en_US.lang");
        write(asset,"item.demo.name=Demo");
        BedrockPackCompiler compiler = new BedrockPackCompiler();
        BedrockPackCompiler.Result first = compiler.compile(root,out);
        try (ZipFile zip = new ZipFile(first.directory.resolve("voxelcore.mcpack").toFile())) {
            assertNotNull(zip.getEntry("texts/en_US.lang"));
        }
        Files.delete(asset);
        BedrockPackCompiler.Result second = compiler.compile(root,out);
        try (ZipFile zip = new ZipFile(second.directory.resolve("voxelcore.mcpack").toFile())) {
            assertNull(zip.getEntry("texts/en_US.lang"));
        }
    }
    @Test public void exportsAuthoredDefaultFontBitmapIconsButNotReservedOrHugeOnes() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        Path icons = root.resolve("demo/assets/demo/textures/font/ranks.png");
        Files.createDirectories(icons.getParent());
        java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(24,8,2);
        sheet.setRGB(2,3,0xffff0000);   // cell 0: U+E300
        sheet.setRGB(10,3,0xff00ff00);  // cell 1: U+E010 (Bedrock controller page, skipped)
        // cell 2: U+E301 left blank, skipped
        javax.imageio.ImageIO.write(sheet,"png",icons.toFile());
        Path huge = root.resolve("demo/assets/demo/textures/font/background.png");
        java.awt.image.BufferedImage background = new java.awt.image.BufferedImage(176,166,2);
        for (int y=0;y<166;y++) for (int x=0;x<176;x++) background.setRGB(x,y,0xff202020);
        javax.imageio.ImageIO.write(background,"png",huge.toFile());
        write(root.resolve("demo/assets/minecraft/font/default.json"), "{\"providers\":["
                + "{\"type\":\"bitmap\",\"file\":\"demo:font/ranks.png\",\"ascent\":7,\"height\":8,\"chars\":[\"\\ue300\\ue010\\ue301\"]},"
                + "{\"type\":\"bitmap\",\"file\":\"demo:font/background.png\",\"ascent\":13,\"height\":166,\"chars\":[\"\\uf000\"]}]}");
        Path allocations = temp.newFolder("glyph-authored").toPath().resolve("glyph-allocations.yml");
        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(root,out,allocations);
        assertEquals(1,result.mappedGlyphs);
        try (ZipFile zip = new ZipFile(result.directory.resolve("voxelcore.mcpack").toFile())) {
            assertNull("Controller pages must never be modified", zip.getEntry("font/glyph_E0.png"));
            assertNull("Backgrounds are not squeezed into text cells", zip.getEntry("font/glyph_F0.png"));
            try (java.io.InputStream image = zip.getInputStream(zip.getEntry("font/glyph_E3.png"))) {
                java.awt.image.BufferedImage atlas = javax.imageio.ImageIO.read(image);
                assertEquals("U+E300 cell holds the red icon", 0xffff0000, atlas.getRGB(7,7));
                assertEquals("U+E301 blank cell stays empty", 0, atlas.getRGB(32+8,8) >>> 24);
            }
        }
    }

    @Test public void exportsManagedInlineGlyphsToSafeBedrockFontPages() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        write(root.resolve("demo/content/ui.yml"),
                "ui:\n  rank_icon:\n    path: ui/rank.png\n    scale_ratio: 16\n");
        Path file = root.resolve("demo/assets/demo/textures/ui/rank.png");
        Files.createDirectories(file.getParent());
        java.awt.image.BufferedImage bitmap = new java.awt.image.BufferedImage(16,16,2);
        bitmap.setRGB(8,8,0xff11cc55);
        javax.imageio.ImageIO.write(bitmap,"png",file.toFile());
        Path allocations = temp.newFolder("glyph-state").toPath().resolve("glyph-allocations.yml");
        new UiGlyphLoader().load(root, allocations, true);
        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(root,out,allocations);
        assertEquals(1,result.mappedGlyphs);
        try (ZipFile zip = new ZipFile(result.directory.resolve("voxelcore.mcpack").toFile())) {
            assertNotNull(zip.getEntry("font/glyph_E2.png"));
            assertNull("Controller pages must never be modified", zip.getEntry("font/glyph_E0.png"));
            assertNull("Controller pages must never be modified", zip.getEntry("font/glyph_E1.png"));
            try (java.io.InputStream image = zip.getInputStream(zip.getEntry("font/glyph_E2.png"))) {
                java.awt.image.BufferedImage atlas = javax.imageio.ImageIO.read(image);
                assertTrue((atlas.getRGB(8,8) >>> 24) != 0);
            }
        }
    }
    @Test public void highResolutionManagedGlyphIsScaledAndExtremeTagIsReported() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        write(root.resolve("demo/content/ui.yml"), "ui:\n  rank_player:\n    path: ui/player.png\n    scale_ratio: 9\n"
                + "  banner_tag:\n    path: ui/banner.png\n    scale_ratio: 8\n");
        Path dir = root.resolve("demo/assets/demo/textures/ui");
        Files.createDirectories(dir);
        java.awt.image.BufferedImage player = new java.awt.image.BufferedImage(64,64,2);
        for (int y=0;y<64;y++) for (int x=0;x<64;x++) player.setRGB(x,y,0xff3366ff);
        javax.imageio.ImageIO.write(player,"png",dir.resolve("player.png").toFile());
        java.awt.image.BufferedImage banner = new java.awt.image.BufferedImage(100,10,2);
        for (int y=0;y<10;y++) for (int x=0;x<100;x++) banner.setRGB(x,y,0xffffffff);
        javax.imageio.ImageIO.write(banner,"png",dir.resolve("banner.png").toFile());
        Path allocations = temp.newFolder("glyph-hires").toPath().resolve("glyph-allocations.yml");
        new UiGlyphLoader().load(root, allocations, true);
        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(root,out,allocations);
        assertEquals("64px icons were skipped before; now scaled into the cell", 2, result.mappedGlyphs);
        // The 100x10 tag is cut into ten 10x10 tiles at Bedrock-only codepoints.
        JsonObject tiles = JsonParser.parseString(new String(Files.readAllBytes(
                result.directory.resolve(BedrockPackCompiler.GLYPH_TILES)), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("tiles");
        assertEquals(1, tiles.size());
        JsonArray sequence = tiles.entrySet().iterator().next().getValue().getAsJsonArray();
        assertEquals(10, sequence.size());
        assertEquals("F400", sequence.get(0).getAsString());
        assertArrayEquals("Stable copy for the plugin", Files.readAllBytes(result.directory.resolve(BedrockPackCompiler.GLYPH_TILES)),
                Files.readAllBytes(out.resolve(BedrockPackCompiler.GLYPH_TILES)));
        try (ZipFile zip = new ZipFile(result.directory.resolve("voxelcore.mcpack").toFile())) {
            try (java.io.InputStream image = zip.getInputStream(zip.getEntry("font/glyph_E2.png"))) {
                java.awt.image.BufferedImage atlas = javax.imageio.ImageIO.read(image);
                assertEquals(BedrockGlyphAtlas.SHEET_SIZE, atlas.getWidth());
            }
            try (java.io.InputStream image = zip.getInputStream(zip.getEntry("font/glyph_F4.png"))) {
                java.awt.image.BufferedImage atlas = javax.imageio.ImageIO.read(image);
                // 10px tile at integer scale 3 in a 32px cell: opaque at the cell's left edge, row 15.
                assertEquals(0xffffffff, atlas.getRGB(0, 15));
                assertEquals(0xffffffff, atlas.getRGB(29, 15));
                assertEquals("Tile area beyond 30px stays empty", 0, atlas.getRGB(31, 15) >>> 24);
            }
        }
    }
    @Test public void supportsGuiRotationWithoutExplicitTextureOverride() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        Path items = root.resolve("demo/content/items.yml");
        String content = new String(Files.readAllBytes(items), StandardCharsets.UTF_8);
        content = content.replace("        texture: demo:item/token", "        gui_rotation: [20, 45, 0]");
        write(items, content);
        write(root.resolve("demo/assets/demo/models/item/token.json"),
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:item/token\"}}");
        BedrockPackCompiler.Result built = new BedrockPackCompiler().compile(root,out);
        assertEquals(1,built.mappedItems);
    }

    @Test public void automaticallyMapsGeneratedModelAndCachesIcon() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        Path items = root.resolve("demo/content/items.yml");
        String content = new String(Files.readAllBytes(items), StandardCharsets.UTF_8);
        content = content.replace("    properties:\n      bedrock:\n        texture: demo:item/token\n", "");
        write(items, content);
        write(root.resolve("demo/assets/demo/models/item/token.json"),
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:item/token\"}}");
        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(root,out);
        assertEquals(1,result.mappedItems);
        try (java.util.stream.Stream<Path> files = Files.list(out.resolve("icon-cache"))) { assertEquals(1,files.count()); }
        assertEquals(result.revision,new BedrockPackCompiler().compile(root,out).revision);
    }
    @Test public void mapsRenderRuleLeavesToPrioritizedGeyserPredicates() throws Exception {
        Path root = temp.newFolder().toPath(), out = temp.newFolder().toPath(), data = temp.newFolder().toPath();
        write(root.resolve("demo/pack.yml"), "schema: 1\nnamespace: demo\n");
        write(root.resolve("demo/content/items.yml"), "items:\n  gem:\n    material: minecraft:paper\n    render:\n"
                + "      model: demo:item/gem\n      custom_model_data:\n        variant: red\n"
                + "      rule:\n        select:\n          key: variant\n          cases:\n            red: demo:item/gem_red\n"
                + "          fallback: demo:item/gem\n");
        for (String name : new String[]{"gem", "gem_red"}) {
            Path texture = root.resolve("demo/assets/demo/textures/item/" + name + ".png");
            Files.createDirectories(texture.getParent());
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(16,16,2);
            image.setRGB(8,8,name.equals("gem") ? 0xff00ff00 : 0xffff0000);
            javax.imageio.ImageIO.write(image, "png", texture.toFile());
            write(root.resolve("demo/assets/demo/models/item/" + name + ".json"),
                    "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:item/" + name + "\"}}");
        }
        try {
            new BedrockPackCompiler().compile(root, out);
            fail("Conditional items are reported, not guessed, without allocations");
        } catch (java.io.IOException nothingMapped) {
            assertTrue(nothingMapped.getMessage().contains("No Bedrock assets or mapped items"));
        }

        // Same stable allocations the Java pack build persists beside glyph-allocations.yml.
        new org.voxelhorizons.content.render.RenderAllocationStore(data.resolve("render-allocations.yml")).save(
                org.voxelhorizons.content.render.RenderAllocationRegistry.reconcile(
                        new org.voxelhorizons.content.load.ContentLoader().load(root),
                        org.voxelhorizons.content.render.RenderAllocationRegistry.empty()));
        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(root, out, data.resolve("glyph-allocations.yml"));
        assertEquals(2, result.mappedItems);
        com.google.gson.JsonArray definitions = com.google.gson.JsonParser.parseString(new String(Files.readAllBytes(
                result.directory.resolve("voxelcore-items.json")), StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonObject("items").getAsJsonArray("minecraft:paper");
        assertEquals(2, definitions.size());
        com.google.gson.JsonObject red = null, fallback = null;
        for (com.google.gson.JsonElement element : definitions) {
            com.google.gson.JsonObject definition = element.getAsJsonObject();
            assertEquals("Every leaf keeps the Java item_model", "demo:item/gem", definition.get("model").getAsString());
            if (definition.has("predicate") && definition.getAsJsonArray("predicate").size() == 1) red = definition;
            else fallback = definition;
        }
        assertNotNull(red); assertNotNull(fallback);
        assertEquals("red", red.getAsJsonArray("predicate").get(0).getAsJsonObject().get("value").getAsString());
        assertTrue(red.get("priority").getAsInt() > fallback.get("priority").getAsInt());
        assertNotEquals(red.get("bedrock_identifier"), fallback.get("bedrock_identifier"));
    }

    @Test public void legacyBackendsMapByStableCustomModelDataNumber() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath(), data = temp.newFolder().toPath();
        try {
            new BedrockPackCompiler().compile(root, out, data.resolve("glyph-allocations.yml"), JavaPackMode.NUMERIC_CUSTOM_MODEL_DATA);
            fail("Legacy mapping without allocations must not guess numbers");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("No Bedrock assets or mapped items"));
        }
        org.voxelhorizons.content.render.RenderAllocationRegistry allocations =
                org.voxelhorizons.content.render.RenderAllocationRegistry.reconcile(
                        new org.voxelhorizons.content.load.ContentLoader().load(root),
                        org.voxelhorizons.content.render.RenderAllocationRegistry.empty());
        new org.voxelhorizons.content.render.RenderAllocationStore(data.resolve("render-allocations.yml")).save(allocations);
        BedrockPackCompiler.Result result = new BedrockPackCompiler().compile(root, out, data.resolve("glyph-allocations.yml"),
                JavaPackMode.NUMERIC_CUSTOM_MODEL_DATA);
        assertEquals(1, result.mappedItems);
        com.google.gson.JsonObject definition = com.google.gson.JsonParser.parseString(new String(Files.readAllBytes(
                result.directory.resolve("voxelcore-items.json")), StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonObject("items").getAsJsonArray("minecraft:paper").get(0).getAsJsonObject();
        assertEquals("legacy", definition.get("type").getAsString());
        assertFalse(definition.has("model"));
        assertEquals(allocations.get(org.voxelhorizons.content.ContentID.parse("demo:token", "demo")).get().customModelData(),
                definition.get("custom_model_data").getAsInt());
        try {
            new BedrockPackCompiler().compile(root, out, null, JavaPackMode.LEGACY_DAMAGE_UNBREAKABLE);
            fail("damage-based backends have no Geyser predicate");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("custom_model_data"));
        }
    }

    @Test public void mergesDisjointNativeBedrockJsonAssets() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        write(root.resolve("other/pack.yml"), "schema: 1\nnamespace: other\n");
        write(root.resolve("demo/bedrock/resource_pack/ui/_global_variables.json"), "{\"first\": 1}");
        write(root.resolve("other/bedrock/resource_pack/ui/_global_variables.json"), "{\"second\": 2}");
        BedrockPackCompiler.Result built = new BedrockPackCompiler().compile(root, out);
        try (ZipFile zip = new ZipFile(built.directory.resolve("voxelcore.mcpack").toFile())) {
            byte[] contents;
            try (java.io.InputStream input = zip.getInputStream(zip.getEntry("ui/_global_variables.json"))) {
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] bytes = new byte[1024]; int n;
                while ((n = input.read(bytes)) != -1) buffer.write(bytes, 0, n);
                contents = buffer.toByteArray();
            }
            JsonObject value = JsonParser.parseString(new String(contents, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(1, value.get("first").getAsInt());
            assertEquals(2, value.get("second").getAsInt());
        }
    }
    @Test public void rejectsJavaFormatAssetsMisplacedInBedrockPack() throws Exception {
        Path root = fixture();
        write(root.resolve("demo/bedrock/resource_pack/assets/minecraft/font/default.json"), "{}");
        try { new BedrockPackCompiler().compile(root, temp.newFolder().toPath()); fail(); }
        catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("Java Edition asset")); }
    }
    @Test public void mapsRenderableAbstractUiItems() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        Path items = root.resolve("demo/content/items.yml");
        String content = new String(Files.readAllBytes(items), StandardCharsets.UTF_8);
        content += "  menu_icon:\n    abstract: true\n    material: minecraft:paper\n"
                + "    render:\n      model: demo:ui/menu_icon\n"
                + "    properties:\n      bedrock:\n        texture: demo:item/token\n";
        write(items, content);
        BedrockPackCompiler.Result built = new BedrockPackCompiler().compile(root, out);
        assertEquals(2, built.mappedItems);
        JsonObject mappings = JsonParser.parseString(new String(
                Files.readAllBytes(built.directory.resolve("voxelcore-items.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray paper = mappings.getAsJsonObject("items").getAsJsonArray("minecraft:paper");
        assertEquals(2, paper.size());
        boolean abstractMapped = false;
        for (JsonElement entry : paper) {
            if ("demo:ui/menu_icon".equals(entry.getAsJsonObject().get("model").getAsString()))
                abstractMapped = true;
        }
        assertTrue("Abstract GUI items must be mapped for Bedrock inventory rendering", abstractMapped);
    }
    @Test public void ignoresUnrelatedJavaAssetCollisions() throws Exception {
        Path root = fixture(), out = temp.newFolder().toPath();
        write(root.resolve("other/pack.yml"), "schema: 1\nnamespace: other\n");
        for (String path : new String[]{"font/default.json", "lang/en_us.json", "atlases/items.json", "blockstates/stone.json"}) {
            write(root.resolve("demo/assets/minecraft/" + path), "{\"a\":1}");
            write(root.resolve("other/assets/minecraft/" + path), "{\"b\":2}");
        }
        assertEquals(1, new BedrockPackCompiler().compile(root,out).mappedItems);
    }
    @Test public void unrelatedConflictingModelsDoNotAbortBuild() throws Exception {
        Path root = fixture();
        write(root.resolve("other/pack.yml"), "schema: 1\nnamespace: other\n");
        write(root.resolve("demo/assets/minecraft/models/item/test.json"), "{\"parent\":\"demo:a\"}");
        write(root.resolve("other/assets/minecraft/models/item/test.json"), "{\"parent\":\"demo:b\"}");
        assertEquals(1, new BedrockPackCompiler().compile(root,temp.newFolder().toPath()).mappedItems);
    }
    private static void write(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent()); Files.write(path,text.getBytes(StandardCharsets.UTF_8));
    }
}
