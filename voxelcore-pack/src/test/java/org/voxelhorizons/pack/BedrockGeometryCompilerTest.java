package org.voxelhorizons.pack;

import com.google.gson.*;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.*;

public final class BedrockGeometryCompilerTest {
    private static byte[] png(int color) throws IOException {
        BufferedImage bitmap = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++) for(int x=0;x<16;x++) bitmap.setRGB(x,y,color);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(bitmap,"png",bytes);
        return bytes.toByteArray();
    }
    private static Map<String,byte[]> modelAssets() throws IOException {
        Map<String,byte[]> files = new TreeMap<String,byte[]>();
        files.put("assets/demo/textures/oak.png",png(0xffaa4422));
        files.put("assets/demo/textures/dark.png",png(0xff443322));
        files.put("assets/demo/models/bench.json",(
                "{\"textures\":{\"wood\":\"demo:oak\"},\"elements\":["
                + "{\"from\":[-8,0,0],\"to\":[24,4,16],\"faces\":{"
                + "\"north\":{\"texture\":\"#wood\"},\"up\":{\"texture\":\"#wood\"}}}]}").getBytes(StandardCharsets.UTF_8));
        files.put("assets/demo/models/dark_bench.json",(
                "{\"parent\":\"demo:bench\",\"textures\":{\"wood\":\"demo:dark\"}}").getBytes(StandardCharsets.UTF_8));
        return files;
    }

    @Test public void exportsNativeGeometryAttachableAndParentTextureOverride() throws Exception {
        BedrockGeometryCompiler converter = new BedrockGeometryCompiler(modelAssets());
        String identifier = "voxelcore:i_11112222333344445555666677778888";
        String shortId = "i_11112222333344445555666677778888";
        BedrockGeometryCompiler.Output first = converter.compile("demo:bench",identifier,shortId);
        BedrockGeometryCompiler.Output child = converter.compile("demo:dark_bench",identifier,shortId);
        assertNotNull(first); assertEquals(1,first.cuboids); assertEquals(6,first.assets.size());
        assertTrue(first.assets.containsKey(BedrockGeometryCompiler.TRANSFORM_ANIMATION_PATH));
        assertTrue(first.assets.containsKey("attachables/"+shortId+".json"));
        assertTrue(first.assets.containsKey("models/entity/voxelcore/"+shortId+".geo.json"));
        assertTrue(first.assets.containsKey("textures/voxelcore/g_"+shortId+".png"));
        JsonObject entity = JsonParser.parseString(new String(first.assets.get(
                "entity/voxelcore/"+shortId+".entity.json"),StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonObject("minecraft:client_entity").getAsJsonObject("description");
        assertEquals("voxelcore:entity_"+shortId, entity.get("identifier").getAsString());
        assertEquals("geometry.voxelcore."+shortId,
                entity.getAsJsonObject("geometry").get("default").getAsString());
        JsonObject geometry = JsonParser.parseString(new String(first.assets.get(
                "models/entity/voxelcore/"+shortId+".geo.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("1.16.0",geometry.get("format_version").getAsString());
        JsonObject cube = geometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones").get(0).getAsJsonObject().getAsJsonArray("cubes").get(0).getAsJsonObject();
        assertEquals(32,cube.getAsJsonArray("size").get(0).getAsInt());
        assertEquals(-16,cube.getAsJsonArray("origin").get(0).getAsInt());
        assertNotNull(cube.getAsJsonObject("uv").getAsJsonObject("north"));
        JsonObject attachable = JsonParser.parseString(new String(first.assets.get(
                "attachables/"+shortId+".json"),StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(identifier,attachable.getAsJsonObject("minecraft:attachable")
                .getAsJsonObject("description").get("identifier").getAsString());
        BufferedImage wood = ImageIO.read(new ByteArrayInputStream(first.assets.get(
                "textures/voxelcore/g_"+shortId+".png")));
        BufferedImage dark = ImageIO.read(new ByteArrayInputStream(child.assets.get(
                "textures/voxelcore/g_"+shortId+".png")));
        assertEquals(0xffaa4422,wood.getRGB(1,1));
        assertEquals(0xff443322,dark.getRGB(1,1));
        for(String path:first.assets.keySet())assertTrue(path,path.getBytes(StandardCharsets.UTF_8).length<80);
        BedrockGeometryCompiler.Output repeated = converter.compile("demo:bench",identifier,shortId);
        for(String path:first.assets.keySet())assertArrayEquals(first.assets.get(path),repeated.assets.get(path));
    }

    @Test public void flatGeneratedSpriteHasNoGeometry() throws Exception {
        Map<String,byte[]> assets = new TreeMap<String,byte[]>();
        assets.put("assets/demo/models/token.json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:token\"}}"
                        .getBytes(StandardCharsets.UTF_8));
        assertNull(new BedrockGeometryCompiler(assets).compile("demo:token","demo:token","token"));
    }

    @Test public void rejectsUntranslatableTintedFacesRatherThanClaimingParity() throws Exception {
        Map<String,byte[]> assets = modelAssets();
        String parent = new String(assets.get("assets/demo/models/bench.json"),StandardCharsets.UTF_8);
        parent = parent.replace("\"texture\":\"#wood\"","\"texture\":\"#wood\",\"tintindex\":0");
        assets.put("assets/demo/models/bench.json",parent.getBytes(StandardCharsets.UTF_8));
        try {new BedrockGeometryCompiler(assets).compile("demo:bench","demo:bench","bench");fail("tint is not supported");}
        catch(IOException expected){assertTrue(expected.getMessage().contains("Tinted"));}
    }

    @Test public void convertsJavaRescaleIntoExactPerpendicularCuboidStretch() throws Exception {
        Map<String,byte[]> assets = modelAssets();
        String parent = new String(assets.get("assets/demo/models/bench.json"),StandardCharsets.UTF_8);
        parent = parent.replace("\"faces\":{",
            "\"rotation\":{\"axis\":\"y\",\"angle\":45,\"origin\":[8,8,8],\"rescale\":true},\"faces\":{");
        assets.put("assets/demo/models/bench.json",parent.getBytes(StandardCharsets.UTF_8));
        String shortId = "i_11112222333344445555666677778888";
        BedrockGeometryCompiler.Output output = new BedrockGeometryCompiler(assets)
                .compile("demo:bench","voxelcore:"+shortId,shortId);
        JsonObject cube = JsonParser.parseString(new String(output.assets.get(
                "models/entity/voxelcore/"+shortId+".geo.json"),StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones").get(0).getAsJsonObject().getAsJsonArray("cubes").get(0).getAsJsonObject();
        double stretch = Math.sqrt(2.0); // 1/cos(45 deg)
        // x and z (perpendicular to the y rotation axis) stretch about the origin; y is untouched.
        assertEquals(32*stretch, cube.getAsJsonArray("size").get(0).getAsDouble(), 1e-6);
        assertEquals(4, cube.getAsJsonArray("size").get(1).getAsDouble(), 1e-6);
        assertEquals(16*stretch, cube.getAsJsonArray("size").get(2).getAsDouble(), 1e-6);
        assertEquals((8+(-8-8)*stretch)-8, cube.getAsJsonArray("origin").get(0).getAsDouble(), 1e-6);
        assertEquals(-8, cube.getAsJsonArray("origin").get(1).getAsDouble(), 1e-6);
        assertEquals((8+(0-8)*stretch)-8, cube.getAsJsonArray("origin").get(2).getAsDouble(), 1e-6);
        assertEquals("Java Y rotation is negated for Bedrock (mirrored X)", -45, cube.getAsJsonArray("rotation").get(1).getAsDouble(), 1e-6);
    }

    @Test public void mirrorsXAndBakesFixedDisplayScaleIntoPlacedGeometryOnly() throws Exception {
        Map<String,byte[]> assets = modelAssets();
        assets.put("assets/demo/models/corner.json",("{\"display\":{\"fixed\":{\"scale\":[0.5,0.5,0.5]}},"
                + "\"textures\":{\"wood\":\"demo:oak\"},\"elements\":["
                + "{\"from\":[0,0,0],\"to\":[4,16,16],\"faces\":{\"north\":{\"texture\":\"#wood\"}}}]}")
                .getBytes(StandardCharsets.UTF_8));
        String shortId = "i_11112222333344445555666677778888";
        BedrockGeometryCompiler.Output output = new BedrockGeometryCompiler(assets).compile("demo:corner","voxelcore:"+shortId,shortId);
        JsonArray geometries = JsonParser.parseString(new String(output.assets.get("models/entity/voxelcore/"+shortId+".geo.json"),
                StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry");
        JsonObject placed = geometries.get(0).getAsJsonObject().getAsJsonArray("bones").get(0).getAsJsonObject()
                .getAsJsonArray("cubes").get(0).getAsJsonObject();
        // Java x 0..4 (west side) is Bedrock x 4..8 before scaling; display.fixed halves it about the centre.
        assertEquals("[2.0,-4.0,-4.0]", placed.get("origin").toString());
        assertEquals("[2.0,8.0,8.0]", placed.get("size").toString());
        JsonObject held = geometries.get(1).getAsJsonObject().getAsJsonArray("bones").get(4).getAsJsonObject()
                .getAsJsonArray("cubes").get(0).getAsJsonObject();
        assertEquals("Held geometry uses the hand transforms, not display.fixed", "[4.0,0.0,-8.0]", held.get("origin").toString());
    }

    @Test public void keepsFlatPlaneElementsButRejectsLines() throws Exception {
        Map<String,byte[]> assets = modelAssets();
        assets.put("assets/demo/models/plane.json",("{\"textures\":{\"wood\":\"demo:oak\"},\"elements\":["
                + "{\"from\":[0,0,8],\"to\":[16,16,8],\"faces\":{\"north\":{\"texture\":\"#wood\"},"
                + "\"south\":{\"texture\":\"#wood\"}}}]}").getBytes(StandardCharsets.UTF_8));
        String shortId = "i_11112222333344445555666677778888";
        BedrockGeometryCompiler.Output output = new BedrockGeometryCompiler(assets)
                .compile("demo:plane","voxelcore:"+shortId,shortId);
        assertEquals(1, output.cuboids);
        assets.put("assets/demo/models/line.json",("{\"textures\":{\"wood\":\"demo:oak\"},\"elements\":["
                + "{\"from\":[0,8,8],\"to\":[16,8,8],\"faces\":{\"north\":{\"texture\":\"#wood\"}}}]}")
                .getBytes(StandardCharsets.UTF_8));
        try { new BedrockGeometryCompiler(assets).compile("demo:line","voxelcore:"+shortId,shortId); fail("line"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("Degenerate")); }
    }

    @Test public void acceptsPerAxisRotationAndInvertedBounds() throws Exception {
        Map<String,byte[]> assets = modelAssets();
        assets.put("assets/demo/models/tilted.json",("{\"textures\":{\"wood\":\"demo:oak\"},\"elements\":["
                + "{\"from\":[12,4,12],\"to\":[4,0,4],\"rotation\":{\"x\":10,\"y\":60,\"origin\":[8,8,8]},"
                + "\"faces\":{\"north\":{\"texture\":\"#wood\"},\"up\":{\"texture\":\"#wood\"}}}]}")
                .getBytes(StandardCharsets.UTF_8));
        String shortId = "i_11112222333344445555666677778888";
        BedrockGeometryCompiler.Output output = new BedrockGeometryCompiler(assets)
                .compile("demo:tilted","voxelcore:"+shortId,shortId);
        JsonObject cube = JsonParser.parseString(new String(output.assets.get("models/entity/voxelcore/"+shortId+".geo.json"),
                StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones").get(0).getAsJsonObject().getAsJsonArray("cubes").get(0).getAsJsonObject();
        assertEquals("[-4.0,-8.0,-4.0]", cube.get("origin").toString());
        assertEquals("[8.0,4.0,8.0]", cube.get("size").toString());
        assertEquals(-10.0, cube.getAsJsonArray("rotation").get(0).getAsDouble(), 0);
        assertEquals(-60.0, cube.getAsJsonArray("rotation").get(1).getAsDouble(), 0);
        assertNotNull(new BedrockIconRenderer(assets).render("demo:tilted", null, null));
    }

    @Test public void bakesConfiguredConstantTintIntoTintedFaces() throws Exception {
        Map<String,byte[]> assets = modelAssets();
        String parent = new String(assets.get("assets/demo/models/bench.json"),StandardCharsets.UTF_8);
        parent = parent.replace("\"texture\":\"#wood\"","\"texture\":\"#wood\",\"tintindex\":0");
        assets.put("assets/demo/models/bench.json",parent.getBytes(StandardCharsets.UTF_8));
        String shortId = "i_11112222333344445555666677778888";
        Map<Integer,Integer> tints = BedrockTints.parse(java.util.Collections.singletonMap("0","#808080"),"demo:bench");
        BedrockGeometryCompiler.Output output = new BedrockGeometryCompiler(assets)
                .compile("demo:bench","voxelcore:"+shortId,shortId,tints);
        BufferedImage atlas = ImageIO.read(new ByteArrayInputStream(output.assets.get("textures/voxelcore/g_"+shortId+".png")));
        // 0xaa4422 multiplied by 0x80/0xff per channel.
        assertEquals(0xff552211, atlas.getRGB(0,0));
    }

    @Test public void dyeableTintedModelGetsDefaultPlusSixteenDyeAtlasesAndController() throws Exception {
        Map<String,byte[]> assets = modelAssets();
        String parent = new String(assets.get("assets/demo/models/bench.json"),StandardCharsets.UTF_8);
        parent = parent.replace("\"texture\":\"#wood\"","\"texture\":\"#wood\",\"tintindex\":0");
        assets.put("assets/demo/models/bench.json",parent.getBytes(StandardCharsets.UTF_8));
        String shortId = "i_11112222333344445555666677778888";
        BedrockGeometryCompiler.Output output = new BedrockGeometryCompiler(assets).compileDyeable("demo:bench",
                "voxelcore:"+shortId, shortId, java.util.Collections.<Integer,Integer>emptyMap(), 0xFFFFFF);
        assertTrue(output.tinted);
        JsonObject description = JsonParser.parseString(new String(output.assets.get(
                "entity/voxelcore/"+shortId+".entity.json"),StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonObject("minecraft:client_entity").getAsJsonObject("description");
        assertEquals(BedrockGeometryCompiler.DYE_CONTROLLER, description.getAsJsonArray("render_controllers").get(0).getAsString());
        assertEquals(17, description.getAsJsonObject("textures").size());
        // Default colour white keeps the texture; red dye (0xB02E26) multiplies it.
        BufferedImage undyed = ImageIO.read(new ByteArrayInputStream(output.assets.get("textures/voxelcore/g_"+shortId+".png")));
        BufferedImage red = ImageIO.read(new ByteArrayInputStream(output.assets.get("textures/voxelcore/g_"+shortId+"_d15.png")));
        assertEquals(0xffaa4422, undyed.getRGB(0,0));
        assertEquals(0xff750c05, red.getRGB(0,0));
        String controller = new String(output.assets.get(BedrockGeometryCompiler.DYE_CONTROLLER_PATH), StandardCharsets.UTF_8);
        assertTrue(controller.contains("Array.dye[query.has_property('voxelcore:dye') ? query.property('voxelcore:dye') : 0]"));
        assertTrue(controller.contains("Texture.dye_16"));

        BedrockGeometryCompiler.Output untinted = new BedrockGeometryCompiler(modelAssets()).compileDyeable("demo:bench",
                "voxelcore:"+shortId, shortId, java.util.Collections.<Integer,Integer>emptyMap(), 0xFFFFFF);
        assertFalse("Untinted models stay single-texture", untinted.assets.containsKey(BedrockGeometryCompiler.DYE_CONTROLLER_PATH));
    }

    @Test public void entityAnimatesRootFromGuardedTransformProperties() throws Exception {
        String shortId = "i_11112222333344445555666677778888";
        BedrockGeometryCompiler.Output output = new BedrockGeometryCompiler(modelAssets())
                .compile("demo:bench","voxelcore:"+shortId,shortId);
        JsonObject description = JsonParser.parseString(new String(output.assets.get(
                "entity/voxelcore/"+shortId+".entity.json"),StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonObject("minecraft:client_entity").getAsJsonObject("description");
        assertEquals(BedrockGeometryCompiler.TRANSFORM_ANIMATION,
                description.getAsJsonObject("animations").get("voxelcore_transform").getAsString());
        assertEquals("voxelcore_transform", description.getAsJsonObject("scripts").getAsJsonArray("animate").get(0).getAsString());
        JsonObject bone = JsonParser.parseString(new String(output.assets.get(BedrockGeometryCompiler.TRANSFORM_ANIMATION_PATH),
                StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animations")
                .getAsJsonObject(BedrockGeometryCompiler.TRANSFORM_ANIMATION).getAsJsonObject("bones").getAsJsonObject("root");
        assertEquals("(query.has_property('voxelcore:sx') ? query.property('voxelcore:sx') : 1)",
                bone.getAsJsonArray("scale").get(0).getAsString());
        assertEquals("(query.has_property('voxelcore:tx') ? query.property('voxelcore:tx') : 0) * 16",
                bone.getAsJsonArray("position").get(0).getAsString());
        assertTrue(bone.getAsJsonArray("rotation").get(0).getAsString().startsWith("-(query.has_property('voxelcore:pitch')"));
        // The root bone pivots at the entity origin, matching the Java matrix origin.
        JsonObject geometry = JsonParser.parseString(new String(output.assets.get(
                "models/entity/voxelcore/"+shortId+".geo.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject rootBone = geometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones").get(0).getAsJsonObject();
        assertEquals("root", rootBone.get("name").getAsString());
    }

    @Test public void heldAttachableBindsToHandAndUsesJavaHeldTransform() throws Exception {
        Map<String,byte[]> assets = modelAssets();
        String parent = new String(assets.get("assets/demo/models/bench.json"),StandardCharsets.UTF_8);
        parent = parent.replaceFirst("\\{", "{\"display\":{\"thirdperson_righthand\":{\"rotation\":[10,20,30],\"translation\":[0,3,1],\"scale\":[0.5,0.5,0.5]}},");
        assets.put("assets/demo/models/bench.json",parent.getBytes(StandardCharsets.UTF_8));
        String shortId = "i_11112222333344445555666677778888";
        BedrockGeometryCompiler.Output output = new BedrockGeometryCompiler(assets).compile("demo:bench","voxelcore:"+shortId,shortId);
        JsonObject attachable = JsonParser.parseString(new String(output.assets.get("attachables/"+shortId+".json"),
                StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("minecraft:attachable").getAsJsonObject("description");
        String heldGeometry = attachable.getAsJsonObject("geometry").get("default").getAsString();
        assertEquals("geometry.voxelcore."+shortId+".held", heldGeometry);
        JsonArray geometries = JsonParser.parseString(new String(output.assets.get("models/entity/voxelcore/"+shortId+".geo.json"),
                StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry");
        JsonObject held = geometries.get(1).getAsJsonObject();
        assertEquals(heldGeometry, held.getAsJsonObject("description").get("identifier").getAsString());
        assertEquals("q.item_slot_to_bone_name(c.item_slot)",
                held.getAsJsonArray("bones").get(0).getAsJsonObject().get("binding").getAsString());
        assertEquals("held", held.getAsJsonArray("bones").get(1).getAsJsonObject().get("parent").getAsString());
        JsonObject pose = JsonParser.parseString(new String(output.assets.get("animations/voxelcore_held_"+shortId+".animation.json"),
                StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animations")
                .getAsJsonObject("animation.voxelcore."+shortId+".held.third_person").getAsJsonObject("bones");
        assertEquals("[90.0,0.0,0.0]", pose.getAsJsonObject("held").get("rotation").toString());
        assertEquals("[0.0,13.0,-3.0]", pose.getAsJsonObject("held").get("position").toString());
        assertEquals(0.5, pose.getAsJsonObject("held_x").getAsJsonArray("scale").get(0).getAsDouble(), 0);
        assertEquals(-10, pose.getAsJsonObject("held_x").getAsJsonArray("rotation").get(0).getAsDouble(), 0);
        assertEquals(-20, pose.getAsJsonObject("held_y").getAsJsonArray("rotation").get(1).getAsDouble(), 0);
        assertEquals(30, pose.getAsJsonObject("held_z").getAsJsonArray("rotation").get(2).getAsDouble(), 0);
        JsonArray heldBones = held.getAsJsonArray("bones");
        JsonObject model = heldBones.get(4).getAsJsonObject();
        assertEquals("model", model.get("name").getAsString());
        assertEquals("Cubes in Java item space (y raised by 8)", 0.0,
                model.getAsJsonArray("cubes").get(0).getAsJsonObject().getAsJsonArray("origin").get(1).getAsDouble(), 0);
        assertEquals("Placed entities keep the unbound geometry", "geometry.voxelcore."+shortId,
                geometries.get(0).getAsJsonObject().getAsJsonObject("description").get("identifier").getAsString());
    }

    @Test public void rescaleFactorIsOneWithoutRescaleFlag() throws Exception {
        JsonObject rotation = JsonParser.parseString("{\"axis\":\"x\",\"angle\":22.5}").getAsJsonObject();
        assertEquals(1.0, BedrockIconRenderer.rescaleFactor(rotation), 0);
        rotation.addProperty("rescale", true);
        assertEquals(1/Math.cos(Math.toRadians(22.5)), BedrockIconRenderer.rescaleFactor(rotation), 1e-12);
    }
}
