package org.voxelhorizons.pack;
import org.junit.Test;
import java.util.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.Assert.*;
public class BedrockIconRendererTest {
 @Test public void rendersInheritedCubeAndTransparentBackground() throws Exception {
  Map<String,byte[]> assets=new HashMap<String,byte[]>();
  BufferedImage texture=new BufferedImage(16,16,2);for(int y=0;y<16;y++)for(int x=0;x<16;x++)texture.setRGB(x,y,0xffff0000);
  ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(texture,"png",out);assets.put("assets/demo/textures/red.png",out.toByteArray());
  assets.put("assets/demo/models/base.json",("{\"textures\":{\"all\":\"demo:red\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{\"north\":{\"texture\":\"#all\"},\"south\":{\"texture\":\"#all\"},\"east\":{\"texture\":\"#all\"},\"west\":{\"texture\":\"#all\"},\"up\":{\"texture\":\"#all\"},\"down\":{\"texture\":\"#all\"}}}]}").getBytes("UTF-8"));
  assets.put("assets/demo/models/item.json","{\"parent\":\"demo:base\"}".getBytes("UTF-8"));
  BufferedImage result=ImageIO.read(new ByteArrayInputStream(new BedrockIconRenderer(assets).render("demo:item")));
  assertEquals(64,result.getWidth());assertEquals(0,result.getRGB(0,0));assertEquals(0xffff0000,result.getRGB(32,32));
 }
 @Test public void rendersRescaledRotatedElementInsteadOfRejectingIt() throws Exception {
  Map<String,byte[]> assets=new HashMap<String,byte[]>();
  BufferedImage texture=new BufferedImage(16,16,2);for(int y=0;y<16;y++)for(int x=0;x<16;x++)texture.setRGB(x,y,0xff00ff00);
  ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(texture,"png",out);assets.put("assets/demo/textures/green.png",out.toByteArray());
  assets.put("assets/demo/models/cross.json",("{\"textures\":{\"all\":\"demo:green\"},\"elements\":[{\"from\":[0.8,0,8],\"to\":[15.2,16,8],"
   +"\"rotation\":{\"origin\":[8,8,8],\"axis\":\"y\",\"angle\":-45,\"rescale\":true},"
   +"\"faces\":{\"north\":{\"texture\":\"#all\"},\"south\":{\"texture\":\"#all\"}}}]}").getBytes("UTF-8"));
  BufferedImage result=ImageIO.read(new ByteArrayInputStream(new BedrockIconRenderer(assets).render("demo:cross")));
  assertEquals(0xff00ff00,result.getRGB(32,32));
 }
 @Test public void tintsGeneratedLayerByLayerIndex() throws Exception {
  Map<String,byte[]> assets=new HashMap<String,byte[]>();
  BufferedImage texture=new BufferedImage(16,16,2);for(int y=0;y<16;y++)for(int x=0;x<16;x++)texture.setRGB(x,y,0xffffffff);
  ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(texture,"png",out);assets.put("assets/demo/textures/white.png",out.toByteArray());
  assets.put("assets/demo/models/leaf.json","{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:white\"}}".getBytes("UTF-8"));
  Map<Integer,Integer> tints=BedrockTints.parse(Collections.singletonMap(0,"#48b518"),"demo:leaf");
  BufferedImage result=ImageIO.read(new ByteArrayInputStream(new BedrockIconRenderer(assets).render("demo:leaf",null,tints)));
  assertEquals(0xff48b518,result.getRGB(3,3));
  try{BedrockTints.parse(Collections.singletonMap("x","#000000"),"demo:leaf");fail("index");}catch(IOException expected){}
  try{BedrockTints.parse(Collections.singletonMap("0","green"),"demo:leaf");fail("colour");}catch(IOException expected){}
 }
 @Test public void childTextureOverrideChangesInheritedParentIcon() throws Exception {
  Map<String,byte[]> assets=new HashMap<String,byte[]>();
  BufferedImage red=new BufferedImage(16,16,2),blue=new BufferedImage(16,16,2);
  for(int y=0;y<16;y++)for(int x=0;x<16;x++){red.setRGB(x,y,0xffa00000);blue.setRGB(x,y,0xff0080ff);}
  ByteArrayOutputStream r=new ByteArrayOutputStream(),b=new ByteArrayOutputStream();
  ImageIO.write(red,"png",r);ImageIO.write(blue,"png",b);
  assets.put("assets/demo/textures/red.png",r.toByteArray());
  assets.put("assets/demo/textures/blue.png",b.toByteArray());
  assets.put("assets/demo/models/oak_bench.json",("{\"textures\":{\"main\":\"demo:red\"},\"elements\":[{\"from\":[-8,0,0],\"to\":[24,10,16],\"faces\":{\"north\":{\"texture\":\"#main\"},\"south\":{\"texture\":\"#main\"},\"up\":{\"texture\":\"#main\"}}}]}").getBytes("UTF-8"));
  assets.put("assets/demo/models/dark_oak_bench.json","{\"parent\":\"demo:oak_bench\",\"textures\":{\"main\":\"demo:blue\"}}".getBytes("UTF-8"));
  BedrockIconRenderer renderer=new BedrockIconRenderer(assets);
  BufferedImage parent=ImageIO.read(new ByteArrayInputStream(renderer.render("demo:oak_bench")));
  BufferedImage child=ImageIO.read(new ByteArrayInputStream(renderer.render("demo:dark_oak_bench")));
  int p=0,c=0;for(int y=0;y<64;y++)for(int x=0;x<64;x++) {if((parent.getRGB(x,y)>>>24)!=0)p++;if((child.getRGB(x,y)>>>24)!=0)c++;}
  assertTrue("Parent furniture preview is missing",p>10);
  assertTrue("Child furniture preview is missing",c>10);
  assertNotEquals("Inherited texture override must change icon",parent.getRGB(32,32),child.getRGB(32,32));
 }
 @Test public void resolvesVanillaCubeAllParentNotPresentInAuthoredAssets() throws Exception {
  Map<String,byte[]> assets=new HashMap<String,byte[]>();
  BufferedImage texture=new BufferedImage(16,16,2);
  for(int y=0;y<16;y++)for(int x=0;x<16;x++)texture.setRGB(x,y,0xff228822);
  ByteArrayOutputStream png=new ByteArrayOutputStream();
  ImageIO.write(texture,"png",png);
  assets.put("assets/demo/textures/planks.png",png.toByteArray());
  assets.put("assets/demo/models/oak.json",
      "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"demo:planks\"}}".getBytes("UTF-8"));
  BufferedImage preview=ImageIO.read(new ByteArrayInputStream(new BedrockIconRenderer(assets).render("demo:oak")));
  assertTrue((preview.getRGB(32,32)>>>24)!=0);
 }
 @Test public void compositesGeneratedLayersIncludingInheritedTextures() throws Exception {
  Map<String,byte[]> assets=new HashMap<String,byte[]>();
  BufferedImage background=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
  BufferedImage emblem=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
  for(int y=0;y<16;y++)for(int x=0;x<16;x++)background.setRGB(x,y,0xff994411);
  emblem.setRGB(8,8,0xff55ccee);
  ByteArrayOutputStream red=new ByteArrayOutputStream(),blue=new ByteArrayOutputStream();
  ImageIO.write(background,"png",red);ImageIO.write(emblem,"png",blue);
  assets.put("assets/demo/textures/base.png",red.toByteArray());
  assets.put("assets/demo/textures/emblem.png",blue.toByteArray());
  assets.put("assets/demo/models/base.json",
      ("{\"parent\":\"minecraft:item/generated\",\"textures\":"
       + "{\"layer0\":\"demo:base\",\"particle\":\"demo:base\"}}").getBytes("UTF-8"));
  assets.put("assets/demo/models/child.json",
      "{\"parent\":\"demo:base\",\"textures\":{\"layer1\":\"demo:emblem\"}}".getBytes("UTF-8"));
  BufferedImage preview=ImageIO.read(new ByteArrayInputStream(new BedrockIconRenderer(assets).render("demo:child")));
  assertEquals(0xff994411,preview.getRGB(0,0));
  assertEquals(0xff55ccee,preview.getRGB(8,8));
  assertEquals(16,preview.getWidth());
 }
 @Test public void rejectsMissingOrMismatchedGeneratedLayers() throws Exception {
  Map<String,byte[]> assets=new HashMap<String,byte[]>();
  assets.put("assets/demo/models/gap.json",
      "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:base\",\"layer2\":\"demo:badge\"}}".getBytes("UTF-8"));
  try {new BedrockIconRenderer(assets).render("demo:gap");fail("Missing layer1 must be rejected");}
  catch(IOException expected){assertTrue(expected.getMessage().contains("Gap"));}
 }
 @Test public void rejectsParentCycles() throws Exception {
  Map<String,byte[]> assets=new HashMap<String,byte[]>();assets.put("assets/demo/models/a.json","{\"parent\":\"demo:a\"}".getBytes("UTF-8"));
  try {new BedrockIconRenderer(assets).render("demo:a");fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("cycle"));}
 }
}
