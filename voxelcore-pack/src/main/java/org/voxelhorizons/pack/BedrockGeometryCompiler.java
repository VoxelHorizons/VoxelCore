package org.voxelhorizons.pack;

import com.google.gson.*;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Strict Java cuboid -> native Bedrock entity geometry/attachable converter.
 * This outputs real 3D geometry. Unsupported model features cause an explicit
 * IOException so the caller can retain a 2D icon with a diagnostic instead.
 *
 * This is not a runtime display-entity bridge. Attachable visibility/orientation
 * must still be checked on real Bedrock clients.
 */
final class BedrockGeometryCompiler {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final BedrockIconRenderer resolver;

    BedrockGeometryCompiler(Map<String, byte[]> javaAssets) {
        resolver = new BedrockIconRenderer(javaAssets);
    }

    static final class Output {
        final Map<String, byte[]> assets;
        final int cuboids;
        Output(Map<String, byte[]> assets, int cuboids) {
            this.assets = Collections.unmodifiableMap(new TreeMap<String,byte[]>(assets));
            this.cuboids = cuboids;
        }
    }

    /**
     * @return null for genuinely flat generated item models (no 3D geometry).
     * @throws IOException if 3D geometry exists but uses features not safely translatable.
     */
    Output compile(String javaModel, String bedrockIdentifier, String shortId) throws IOException {
        JsonObject model = resolver.resolvedModel(javaModel);
        if (!model.has("elements")) return null;
        JsonArray elements = model.getAsJsonArray("elements");
        if (elements.size() == 0) return null;
        if (elements.size() > 500)
            throw new IOException("Bedrock cuboid limit exceeded for " + javaModel + ": " + elements.size());
        JsonObject textures = model.has("textures") ? model.getAsJsonObject("textures") : new JsonObject();

        JsonArray cubes = new JsonArray();
        List<BufferedImage> painted = new ArrayList<BufferedImage>();
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (JsonElement entry : elements) {
            JsonObject element = entry.getAsJsonObject();
            double[] from = vector(element,"from"), to = vector(element,"to");
            for (int a=0;a<3;a++) {
                if (to[a] <= from[a] || !Double.isFinite(from[a]) || !Double.isFinite(to[a]))
                    throw new IOException("Invalid/zero-size Java cuboid in " + javaModel);
            }
            JsonObject cube = new JsonObject();
            cube.add("origin", xyz(from[0]-8,from[1]-8,from[2]-8));
            cube.add("size", xyz(to[0]-from[0],to[1]-from[1],to[2]-from[2]));
            for(int a=0;a<3;a++) {
                minX=Math.min(minX,from[0]-8); maxX=Math.max(maxX,to[0]-8);
                minY=Math.min(minY,from[1]-8); maxY=Math.max(maxY,to[1]-8);
                minZ=Math.min(minZ,from[2]-8); maxZ=Math.max(maxZ,to[2]-8);
            }
            if (element.has("rotation")) {
                JsonObject rotation = element.getAsJsonObject("rotation");
                if (rotation.has("rescale") && rotation.get("rescale").getAsBoolean())
                    throw new IOException("Java rescale=true cuboids require geometry transform conversion: " + javaModel);
                String axis = rotation.get("axis").getAsString();
                if (!axis.matches("[xyz]")) throw new IOException("Unknown cuboid rotation axis " + axis);
                double angle = rotation.get("angle").getAsDouble();
                if (!Double.isFinite(angle) || Math.abs(angle) > 45)
                    throw new IOException("Unsupported cuboid rotation " + angle + " in " + javaModel);
                double[] pivot = vector(rotation,"origin");
                double[] rot = new double[3];
                rot["xyz".indexOf(axis)] = angle;
                cube.add("rotation",xyz(rot[0],rot[1],rot[2]));
                cube.add("pivot",xyz(pivot[0]-8,pivot[1]-8,pivot[2]-8));
            }
            JsonObject faces = element.has("faces") ? element.getAsJsonObject("faces") : null;
            if (faces == null || faces.size()==0) throw new IOException("Cuboid without faces in "+javaModel);
            JsonObject mappedFaces = new JsonObject();
            for (Map.Entry<String,JsonElement> side : faces.entrySet()) {
                String direction = side.getKey();
                if (!Arrays.asList("north","south","east","west","up","down").contains(direction))
                    throw new IOException("Unsupported cuboid face " + direction + " in " + javaModel);
                JsonObject face = side.getValue().getAsJsonObject();
                if (face.has("tintindex"))
                    throw new IOException("Tinted cuboid face requires Bedrock tint/variant support: " + javaModel);
                int turn = face.has("rotation") ? face.get("rotation").getAsInt() : 0;
                if (turn % 90 != 0)
                    throw new IOException("Unsupported non-90-degree UV rotation in " + javaModel);
                if (!face.has("texture")) throw new IOException("Cuboid face missing texture in " + javaModel);
                String textureRef = face.get("texture").getAsString();
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(resolver.textureFor(textures,textureRef)));
                if (image==null) throw new IOException("Unreadable model texture "+textureRef);
                double[] uv = face.has("uv") ? vector(face,"uv") : BedrockIconRenderer.uv(direction,from,to);
                if (uv.length!=4) throw new IOException("Expected four Java UV coordinates for " + javaModel);
                BufferedImage tile = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = tile.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g.translate(8,8);
                    g.rotate(Math.toRadians(turn));
                    int sx1=(int)Math.round(uv[0]*image.getWidth()/16.0);
                    int sy1=(int)Math.round(uv[1]*image.getHeight()/16.0);
                    int sx2=(int)Math.round(uv[2]*image.getWidth()/16.0);
                    int sy2=(int)Math.round(uv[3]*image.getHeight()/16.0);
                    g.drawImage(image,-8,-8,8,8,sx1,sy1,sx2,sy2,null);
                } finally { g.dispose(); }
                int index=painted.size();
                painted.add(tile);
                JsonObject mapped = new JsonObject();
                mapped.add("uv",xyz2(index,0)); // Absolute atlas placement fixed below.
                mapped.add("uv_size",xyz2(16,16));
                mappedFaces.add(direction,mapped);
            }
            cube.add("uv",mappedFaces);
            cubes.add(cube);
        }
        if (painted.size() > 4096)
            throw new IOException("Bedrock face atlas requires too many tiles: "+painted.size());
        int columns = 1;
        while (columns*columns < painted.size()) columns*=2;
        int rows = (painted.size()+columns-1)/columns;
        int textureWidth = columns*16, textureHeight=1;
        while(textureHeight < rows*16) textureHeight*=2;
        BufferedImage atlas = new BufferedImage(textureWidth,textureHeight,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        try {
            for (int i=0;i<painted.size();i++)
                graphics.drawImage(painted.get(i),(i%columns)*16,(i/columns)*16,null);
        } finally { graphics.dispose(); }

        // Copy each face UV to its actual native atlas tile, preserving missing faces.
        int next=0;
        for (JsonElement cubeValue : cubes)
            for (Map.Entry<String,JsonElement> face : cubeValue.getAsJsonObject().getAsJsonObject("uv").entrySet()) {
                JsonArray coordinate = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                coordinate.set(0,new JsonPrimitive((next%columns)*16));
                coordinate.set(1,new JsonPrimitive((next/columns)*16));
                next++;
            }

        String geometryId = "geometry.voxelcore." + shortId;
        JsonObject description = new JsonObject();
        description.addProperty("identifier",geometryId);
        description.addProperty("texture_width",textureWidth);
        description.addProperty("texture_height",textureHeight);
        description.addProperty("visible_bounds_width", Math.max(2,Math.max(maxX-minX,maxZ-minZ)/16.0+1));
        description.addProperty("visible_bounds_height", Math.max(2,(maxY-minY)/16.0+1));
        description.add("visible_bounds_offset",xyz((minX+maxX)/32.0,(minY+maxY)/32.0,(minZ+maxZ)/32.0));
        JsonObject bone = new JsonObject();
        bone.addProperty("name","root");
        bone.add("pivot",xyz(0,0,0));
        bone.add("cubes",cubes);
        JsonArray bones = new JsonArray(); bones.add(bone);
        JsonObject geom = new JsonObject();
        geom.add("description",description); geom.add("bones",bones);
        JsonArray geometries = new JsonArray(); geometries.add(geom);
        JsonObject geoRoot = new JsonObject();
        geoRoot.addProperty("format_version","1.12.0");
        geoRoot.add("minecraft:geometry",geometries);

        JsonObject attachDescription = new JsonObject();
        attachDescription.addProperty("identifier",bedrockIdentifier);
        JsonObject materials = new JsonObject();
        materials.addProperty("default","entity_alphatest");
        materials.addProperty("enchanted","entity_alphatest_glint");
        attachDescription.add("materials",materials);
        JsonObject tex = new JsonObject();
        tex.addProperty("default","textures/voxelcore/g_"+shortId);
        tex.addProperty("enchanted","textures/misc/enchanted_item_glint");
        attachDescription.add("textures",tex);
        JsonObject shape = new JsonObject();shape.addProperty("default",geometryId);
        attachDescription.add("geometry",shape);
        JsonArray controllers = new JsonArray();controllers.add("controller.render.item_default");
        attachDescription.add("render_controllers",controllers);
        JsonObject attach = new JsonObject(); attach.add("description",attachDescription);
        JsonObject attachRoot = new JsonObject();
        attachRoot.addProperty("format_version","1.10.0");
        attachRoot.add("minecraft:attachable",attach);

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        if (!ImageIO.write(atlas,"png",png)) throw new IOException("PNG encoder missing");
        Map<String,byte[]> assets = new TreeMap<String,byte[]>();
        assets.put("models/entity/voxelcore/"+shortId+".geo.json",JSON.toJson(geoRoot).getBytes(StandardCharsets.UTF_8));
        assets.put("attachables/"+shortId+".json",JSON.toJson(attachRoot).getBytes(StandardCharsets.UTF_8));
        assets.put("textures/voxelcore/g_"+shortId+".png",png.toByteArray());
        return new Output(assets,cubes.size());
    }

    private static JsonArray xyz(double a,double b,double c) {
        JsonArray x = new JsonArray();x.add(a);x.add(b);x.add(c);return x;
    }
    private static JsonArray xyz2(double a,double b) {
        JsonArray x = new JsonArray();x.add(a);x.add(b);return x;
    }
    private static double[] vector(JsonObject object,String field) throws IOException {
        if (!object.has(field)) throw new IOException("Missing geometry vector "+field);
        JsonArray values = object.getAsJsonArray(field);
        if (values.size()!=3 && values.size()!=4)
            throw new IOException("Invalid geometry vector "+field);
        double[] vector = new double[values.size()];
        for(int i=0;i<vector.length;i++) vector[i]=values.get(i).getAsDouble();
        return vector;
    }
}
