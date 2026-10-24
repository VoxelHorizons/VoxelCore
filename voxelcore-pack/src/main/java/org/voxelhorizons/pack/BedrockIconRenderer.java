package org.voxelhorizons.pack;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Headless orthographic GUI icon renderer. Unsupported dynamic models fail explicitly. */
final class BedrockIconRenderer {
    private final Map<String, byte[]> assets;
    BedrockIconRenderer(Map<String, byte[]> assets) { this.assets = assets; }
    byte[] render(String model) throws IOException {
        return render(model, null);
    }
    byte[] render(String model, double[] guiRotationOverride) throws IOException {
        JsonObject resolved = resolve(model, new HashSet<String>());
        JsonObject textures = resolved.has("textures") ? resolved.getAsJsonObject("textures") : new JsonObject();
        if (!resolved.has("elements")) {
            // Java generated/handheld sprites support multiple layers plus optional
            // particle. Bedrock needs a single icon, so composite the transparent
            // layers in the same order instead of dropping all but layer0.
            if (!textures.has("layer0"))
                throw new IOException("Unsupported generated/special model " + model + ": no layer0");
            java.util.TreeMap<Integer,String> layers = new java.util.TreeMap<Integer,String>();
            for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
                String key = entry.getKey();
                if ("particle".equals(key)) continue;
                if (!key.matches("layer[0-9]+"))
                    throw new IOException("Unsupported generated texture key '" + key + "' in " + model);
                try {
                    int layer = Integer.parseInt(key.substring(5));
                    if (layer >= 32)
                        throw new IOException("Too many generated texture layers in " + model);
                    layers.put(Integer.valueOf(layer), "#" + key);
                } catch (NumberFormatException invalid) {
                    throw new IOException("Invalid generated texture layer " + key + " in " + model, invalid);
                }
            }
            if (layers.size() == 1 && layers.containsKey(Integer.valueOf(0)))
                return texture(textures, "#layer0");
            for (int index=0; index<layers.size(); index++)
                if (!layers.containsKey(Integer.valueOf(index)))
                    throw new IOException("Gap in generated texture layers at layer" + index + " in " + model);
            BufferedImage combined = null;
            for (String layerRef : layers.values()) {
                BufferedImage source = ImageIO.read(new ByteArrayInputStream(texture(textures, layerRef)));
                if (source == null) throw new IOException("Invalid generated layer PNG: " + layerRef);
                if (combined == null) {
                    combined = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
                } else if (combined.getWidth() != source.getWidth() || combined.getHeight() != source.getHeight()) {
                    throw new IOException("Generated layer sizes differ in model " + model);
                }
                java.awt.Graphics2D g = combined.createGraphics();
                try { g.drawImage(source, 0, 0, null); }
                finally { g.dispose(); }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(combined, "png", output))
                throw new IOException("Failed to encode generated layers for " + model);
            return output.toByteArray();
        }
        BufferedImage image = new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        double[] depth = new double[4096]; Arrays.fill(depth, -Double.MAX_VALUE);
        JsonObject gui = new JsonObject();
        if (resolved.has("display") && resolved.getAsJsonObject("display").has("gui")) gui = resolved.getAsJsonObject("display").getAsJsonObject("gui");
        double[] rotation = guiRotationOverride != null ? guiRotationOverride
                : vector(gui,"rotation",new double[]{30,225,0});
        double[] scale = vector(gui,"scale",new double[]{0.625,0.625,0.625});
        double[] translation = vector(gui,"translation",new double[]{0,0,0});
        // Render after collecting every face so long furniture models can be centered
        // and fitted without losing geometry outside the nominal 16x16 item cube.
        List<RenderedFace> faces = new ArrayList<RenderedFace>();
        double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY;
        double maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
        for (JsonElement value : resolved.getAsJsonArray("elements")) {
            JsonObject element=value.getAsJsonObject();
            double[] a=vector(element,"from",null), b=vector(element,"to",null);
            if(a==null||b==null) throw new IOException("Missing element bounds");
            for(Map.Entry<String,JsonElement> entry:element.getAsJsonObject("faces").entrySet()) {
                JsonObject face=entry.getValue().getAsJsonObject();
                if(face.has("tintindex")) throw new IOException("Tinted model needs an explicit Bedrock texture");
                BufferedImage tex=ImageIO.read(new ByteArrayInputStream(texture(textures,face.get("texture").getAsString())));
                if(tex==null) throw new IOException("Invalid PNG");
                double[][] p=face(entry.getKey(),a,b);
                double[] uv=face.has("uv") ? vector(face,"uv",null) : uv(entry.getKey(),a,b);
                int turn=face.has("rotation")?face.get("rotation").getAsInt()/90:0;
                double[][] coords={{uv[0],uv[1]},{uv[2],uv[1]},{uv[2],uv[3]},{uv[0],uv[3]}};
                for(double[] point:p) {
                    if(element.has("rotation")) {
                        JsonObject r=element.getAsJsonObject("rotation");
                        if(r.has("rescale")&&r.get("rescale").getAsBoolean()) throw new IOException("Rescaled element needs explicit texture");
                        double[] origin=vector(r,"origin",new double[]{8,8,8});
                        double[] angles=new double[3]; angles["xyz".indexOf(r.get("axis").getAsString())]=r.get("angle").getAsDouble();
                        for(int i=0;i<3;i++)point[i]-=origin[i]; rotate(point,angles); for(int i=0;i<3;i++)point[i]+=origin[i];
                    }
                    for(int i=0;i<3;i++)point[i]-=8;
                    rotate(point,rotation);
                    for(int i=0;i<3;i++)point[i]=point[i]*scale[i];
                    minX=Math.min(minX,point[0]);maxX=Math.max(maxX,point[0]);
                    minY=Math.min(minY,point[1]);maxY=Math.max(maxY,point[1]);
                }
                faces.add(new RenderedFace(tex,p,coords,turn));
            }
        }
        if (faces.isEmpty()) throw new IOException("Model contains no renderable faces: "+model);
        double fitted = Math.min(4.0, 56.0 / Math.max(0.0001, Math.max(maxX-minX,maxY-minY)));
        double centerX=(minX+maxX)/2.0,centerY=(minY+maxY)/2.0;
        for (RenderedFace part:faces) {
            for(double[] point:part.points) {
                point[0]=32+(point[0]-centerX)*fitted+translation[0]*4;
                point[1]=32-(point[1]-centerY)*fitted-translation[1]*4;
            }
            triangle(image,depth,part.texture,part.points,part.uv,0,1,2,part.turn);
            triangle(image,depth,part.texture,part.points,part.uv,0,2,3,part.turn);
        }
        boolean visible = false;
        for (int y = 0; y < 64 && !visible; y++)
            for (int x = 0; x < 64; x++)
                if ((image.getRGB(x, y) >>> 24) != 0) { visible = true; break; }
        if (!visible) throw new IOException("Model rendered a fully transparent icon: "+model);
        ByteArrayOutputStream out=new ByteArrayOutputStream(); ImageIO.write(image,"png",out); return out.toByteArray();
    }
    private static final class RenderedFace {
        final BufferedImage texture;
        final double[][] points, uv;
        final int turn;
        RenderedFace(BufferedImage texture, double[][] points, double[][] uv, int turn) {
            this.texture=texture;this.points=points;this.uv=uv;this.turn=turn;
        }
    }
    JsonObject resolvedModel(String model) throws IOException { return resolve(model, new HashSet<String>()); }
    byte[] textureFor(JsonObject textures, String reference) throws IOException { return texture(textures, reference); }
    private JsonObject resolve(String id, Set<String> visiting) throws IOException {
        if(!visiting.add(id))throw new IOException("Model parent cycle: "+id);
        byte[] bytes=assets.get(path(id,"models",".json"));
        JsonObject own;
        if (bytes == null) {
            own = builtInParent(id);
            if (own == null) throw new IOException("Missing or conflicting model "+id);
        } else own=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject result=new JsonObject();
        if(own.has("parent")) {
            String parent=own.get("parent").getAsString();
            if(!parent.equals("minecraft:item/generated")&&!parent.equals("item/generated")&&!parent.equals("minecraft:item/handheld"))result=resolve(parent,visiting);
        }
        for(Map.Entry<String,JsonElement> e:own.entrySet()) {
            if((e.getKey().equals("textures")||e.getKey().equals("display"))&&result.has(e.getKey())) {
                JsonObject merged=result.getAsJsonObject(e.getKey());
                for(Map.Entry<String,JsonElement> v:e.getValue().getAsJsonObject().entrySet()) {
                    // Child display.gui often changes only one transform value; do not
                    // discard the inherited rotation, translation and scale.
                    if (e.getKey().equals("display") && merged.has(v.getKey())
                            && merged.get(v.getKey()).isJsonObject() && v.getValue().isJsonObject()) {
                        JsonObject inherited = merged.getAsJsonObject(v.getKey());
                        for (Map.Entry<String,JsonElement> setting : v.getValue().getAsJsonObject().entrySet())
                            inherited.add(setting.getKey(),setting.getValue());
                    } else merged.add(v.getKey(),v.getValue());
                }
            } else result.add(e.getKey(),e.getValue());
        }
        visiting.remove(id); return result;
    }
    /**
     * Vanilla model templates are not stored in authored content packs, but Java
     * models frequently inherit their cube geometry. Resolve those primitives
     * rather than misclassifying every texture-only child as unsupported.
     */
    private static JsonObject builtInParent(String id) {
        String key=id.startsWith("minecraft:")?id.substring("minecraft:".length()):id;
        String faces;
        switch(key) {
            case "block/block": return new JsonObject();
            case "block/cube_all":
                faces="{\"north\":{\"texture\":\"#all\"},\"south\":{\"texture\":\"#all\"},\"east\":{\"texture\":\"#all\"},\"west\":{\"texture\":\"#all\"},\"up\":{\"texture\":\"#all\"},\"down\":{\"texture\":\"#all\"}}";
                break;
            case "block/cube_column":
                faces="{\"north\":{\"texture\":\"#side\"},\"south\":{\"texture\":\"#side\"},\"east\":{\"texture\":\"#side\"},\"west\":{\"texture\":\"#side\"},\"up\":{\"texture\":\"#end\"},\"down\":{\"texture\":\"#end\"}}";
                break;
            case "block/cube_bottom_top":
                faces="{\"north\":{\"texture\":\"#side\"},\"south\":{\"texture\":\"#side\"},\"east\":{\"texture\":\"#side\"},\"west\":{\"texture\":\"#side\"},\"up\":{\"texture\":\"#top\"},\"down\":{\"texture\":\"#bottom\"}}";
                break;
            case "block/cube":
                faces="{\"north\":{\"texture\":\"#north\"},\"south\":{\"texture\":\"#south\"},\"east\":{\"texture\":\"#east\"},\"west\":{\"texture\":\"#west\"},\"up\":{\"texture\":\"#up\"},\"down\":{\"texture\":\"#down\"}}";
                break;
            default:return null;
        }
        return JsonParser.parseString("{\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":"+faces+"}]}").getAsJsonObject();
    }
    private byte[] texture(JsonObject textures,String key)throws IOException {
        Set<String> seen=new HashSet<String>();
        while(key.startsWith("#")) { if(!seen.add(key)||!textures.has(key.substring(1)))throw new IOException("Unresolved texture "+key); key=textures.get(key.substring(1)).getAsString(); }
        byte[] value=assets.get(path(key,"textures",".png")); if(value==null)throw new IOException("Missing or conflicting texture "+key); return value;
    }
    private static String path(String id,String kind,String suffix) { String[] parts=(id.contains(":")?id:"minecraft:"+id).split(":",2); return "assets/"+parts[0]+"/"+kind+"/"+parts[1]+suffix; }
    private static double[] vector(JsonObject o,String key,double[] fallback) { if(!o.has(key))return fallback; JsonArray a=o.getAsJsonArray(key); double[] v=new double[a.size()];for(int i=0;i<v.length;i++)v[i]=a.get(i).getAsDouble();return v; }
    private static void rotate(double[] p,double[] angles) { for(int axis=0;axis<3;axis++){int u=(axis+1)%3,v=(axis+2)%3;double r=Math.toRadians(angles[axis]),x=p[u],y=p[v];p[u]=x*Math.cos(r)-y*Math.sin(r);p[v]=x*Math.sin(r)+y*Math.cos(r);} }
    private static double[][] face(String name,double[] a,double[] b)throws IOException {
        double x=a[0],y=a[1],z=a[2],X=b[0],Y=b[1],Z=b[2];
        switch(name){
            case "north":return new double[][]{{X,Y,z},{x,Y,z},{x,y,z},{X,y,z}};
            case "south":return new double[][]{{x,Y,Z},{X,Y,Z},{X,y,Z},{x,y,Z}};
            case "west":return new double[][]{{x,Y,z},{x,Y,Z},{x,y,Z},{x,y,z}};
            case "east":return new double[][]{{X,Y,Z},{X,Y,z},{X,y,z},{X,y,Z}};
            case "up":return new double[][]{{x,Y,z},{X,Y,z},{X,Y,Z},{x,Y,Z}};
            case "down":return new double[][]{{x,y,Z},{X,y,Z},{X,y,z},{x,y,z}};
            default:throw new IOException("Unknown face "+name);
        }
    }
    static double[] uv(String face,double[] a,double[] b) {
        switch(face){case "up":return new double[]{a[0],a[2],b[0],b[2]};case "down":return new double[]{a[0],16-b[2],b[0],16-a[2]};case "east":return new double[]{16-b[2],16-b[1],16-a[2],16-a[1]};case "west":return new double[]{a[2],16-b[1],b[2],16-a[1]};case "north":return new double[]{16-b[0],16-b[1],16-a[0],16-a[1]};default:return new double[]{a[0],16-b[1],b[0],16-a[1]};}
    }
    private static void triangle(BufferedImage out,double[] depth,BufferedImage texture,double[][] p,double[][] uv,int i,int j,int k,int turn) {
        double[] a=p[i],b=p[j],c=p[k];double den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1]);if(Math.abs(den)<1e-8)return;
        for(int y=0;y<64;y++)for(int x=0;x<64;x++){
            double u=((b[1]-c[1])*(x+.5-c[0])+(c[0]-b[0])*(y+.5-c[1]))/den,v=((c[1]-a[1])*(x+.5-c[0])+(a[0]-c[0])*(y+.5-c[1]))/den,w=1-u-v;
            if(u<0||v<0||w<0)continue;double z=u*a[2]+v*b[2]+w*c[2];if(z<=depth[y*64+x])continue;
            double tx=u*uv[(i+turn)%4][0]+v*uv[(j+turn)%4][0]+w*uv[(k+turn)%4][0],ty=u*uv[(i+turn)%4][1]+v*uv[(j+turn)%4][1]+w*uv[(k+turn)%4][1];
            int color=texture.getRGB(Math.max(0,Math.min(texture.getWidth()-1,(int)(tx*texture.getWidth()/16))),Math.max(0,Math.min(texture.getHeight()-1,(int)(ty*texture.getHeight()/16))));
            if((color>>>24)==0)continue;out.setRGB(x,y,color);depth[y*64+x]=z;
        }
    }
}
