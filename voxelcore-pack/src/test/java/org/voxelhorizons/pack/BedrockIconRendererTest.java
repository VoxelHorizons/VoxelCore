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
 @Test public void rejectsParentCycles() throws Exception {
  Map<String,byte[]> assets=new HashMap<String,byte[]>();assets.put("assets/demo/models/a.json","{\"parent\":\"demo:a\"}".getBytes("UTF-8"));
  try {new BedrockIconRenderer(assets).render("demo:a");fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("cycle"));}
 }
}
