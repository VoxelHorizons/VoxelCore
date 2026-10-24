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
        assertNotNull(first); assertEquals(1,first.cuboids); assertEquals(3,first.assets.size());
        assertTrue(first.assets.containsKey("attachables/"+shortId+".json"));
        assertTrue(first.assets.containsKey("models/entity/voxelcore/"+shortId+".geo.json"));
        assertTrue(first.assets.containsKey("textures/voxelcore/g_"+shortId+".png"));
        JsonObject geometry = JsonParser.parseString(new String(first.assets.get(
                "models/entity/voxelcore/"+shortId+".geo.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("1.12.0",geometry.get("format_version").getAsString());
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

    @Test public void rejectsRescaleWithoutInventingGeometryTransforms() throws Exception {
        Map<String,byte[]> assets = modelAssets();
        String parent = new String(assets.get("assets/demo/models/bench.json"),StandardCharsets.UTF_8);
        parent = parent.replace("\"faces\":{",
            "\"rotation\":{\"axis\":\"y\",\"angle\":45,\"origin\":[8,8,8],\"rescale\":true},\"faces\":{");
        assets.put("assets/demo/models/bench.json",parent.getBytes(StandardCharsets.UTF_8));
        try {new BedrockGeometryCompiler(assets).compile("demo:bench","demo:bench","bench");fail("rescale unsupported");}
        catch(IOException expected){assertTrue(expected.getMessage().contains("rescale"));}
    }
}
