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
        /** True when at least one face had a tintindex (only then do dye variants differ). */
        final boolean tinted;
        Output(Map<String, byte[]> assets, int cuboids, boolean tinted) {
            this.assets = Collections.unmodifiableMap(new TreeMap<String,byte[]>(assets));
            this.cuboids = cuboids;
            this.tinted = tinted;
        }
    }

    /** Java DyeColor#getColor() RGB in DyeColor order (WHITE..BLACK); dye property values 1..16. */
    static final int[] JAVA_DYE_RGB = {0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA,
            0x474F52, 0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};
    static final String DYE_PROPERTY = "voxelcore:dye";
    static final String TRANSFORM_ANIMATION = "animation.voxelcore.transform";
    static final String TRANSFORM_ANIMATION_PATH = "animations/voxelcore_transform.animation.json";

    /**
     * Shared looping client animation applying the Java display transformation that Geyser
     * cannot: local translation (blocks), pitch/roll (degrees) and per-axis scale, read from
     * entity properties set by the VoxelCore Geyser extension. Every channel falls back to
     * identity when the property is absent, so an older extension never hides a model.
     * Properties are Java display-local values. Java's item display renderer turns the item
     * model 180 degrees about Y after the display transformation, and the geometry is X-mirrored
     * (java2bedrock), so in bone space the translation is (x, y, -z) and roll is negated;
     * pitch keeps its sign. Live test: furniture matches Java with this geometry.
     */
    static byte[] transformAnimation() {
        JsonObject root = new JsonObject();
        JsonArray position = new JsonArray();
        position.add(guarded("voxelcore:tx", "0") + " * 16");
        position.add(guarded("voxelcore:ty", "0") + " * 16");
        position.add("-" + guarded("voxelcore:tz", "0") + " * 16");
        root.add("position", position);
        JsonArray rotation = new JsonArray();
        rotation.add("-" + guarded("voxelcore:pitch", "0"));
        rotation.add(0);
        rotation.add("-" + guarded("voxelcore:roll", "0"));
        root.add("rotation", rotation);
        root.add("scale", channels(new String[]{"voxelcore:sx", "voxelcore:sy", "voxelcore:sz"}, "1", ""));
        JsonObject bones = new JsonObject(); bones.add("root", root);
        JsonObject animation = new JsonObject();
        animation.addProperty("loop", true);
        animation.add("bones", bones);
        JsonObject animations = new JsonObject(); animations.add(TRANSFORM_ANIMATION, animation);
        JsonObject file = new JsonObject();
        file.addProperty("format_version", "1.8.0");
        file.add("animations", animations);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(file)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static JsonArray channels(String[] properties, String identity, String suffix) {
        JsonArray out = new JsonArray();
        for (String property : properties) out.add(guarded(property, identity) + suffix);
        return out;
    }

    private static String guarded(String property, String identity) {
        return "(query.has_property('" + property + "') ? query.property('" + property + "') : " + identity + ")";
    }
    static final String DYE_CONTROLLER = "controller.render.voxelcore.dyeable";
    static final String DYE_CONTROLLER_PATH = "render_controllers/voxelcore_dyeable.render_controllers.json";

    /**
     * Shared render controller selecting one of 17 baked atlases with the Geyser entity
     * property {@code voxelcore:dye} (0 = item default colour, 1..16 = Java dye colours),
     * the same Array[...] texture-selection pattern vanilla uses for variants.
     */
    static byte[] dyeRenderController() {
        JsonArray textures = new JsonArray();
        textures.add("Texture.default");
        for (int i = 1; i <= JAVA_DYE_RGB.length; i++) textures.add("Texture.dye_" + i);
        JsonObject arrays = new JsonObject(), textureArrays = new JsonObject();
        textureArrays.add("Array.dye", textures);
        arrays.add("textures", textureArrays);
        JsonObject controller = new JsonObject();
        controller.add("arrays", arrays);
        controller.addProperty("geometry", "Geometry.default");
        JsonArray materials = new JsonArray();
        JsonObject all = new JsonObject(); all.addProperty("*", "Material.default"); materials.add(all);
        controller.add("materials", materials);
        JsonArray selected = new JsonArray();
        // Guarded: an unregistered property would make Molang fail and the entity not render.
        selected.add("Array.dye[query.has_property('" + DYE_PROPERTY + "') ? query.property('" + DYE_PROPERTY + "') : 0]");
        controller.add("textures", selected);
        JsonObject controllers = new JsonObject(); controllers.add(DYE_CONTROLLER, controller);
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.8.0");
        root.add("render_controllers", controllers);
        // Keep Molang quotes literal (Gson would otherwise emit \u0027).
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root)
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Dyeable variant: bakes every tinted face with the item's default colour (texture 0)
     * and each Java dye colour (textures dye_1..dye_16) and switches the client entity to
     * the shared dye render controller. Untinted models are returned unchanged.
     */
    Output compileDyeable(String javaModel, String bedrockIdentifier, String shortId,
                          Map<Integer, Integer> tints, int defaultRgb) throws IOException {
        Output base = compile(javaModel, bedrockIdentifier, shortId, withDye(tints, defaultRgb));
        if (base == null || !base.tinted) return base;
        Map<String, byte[]> assets = new TreeMap<String, byte[]>(base.assets);
        String entityPath = "entity/voxelcore/" + shortId + ".entity.json";
        JsonObject entity = JsonParser.parseString(new String(assets.get(entityPath), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject description = entity.getAsJsonObject("minecraft:client_entity").getAsJsonObject("description");
        for (int i = 0; i < JAVA_DYE_RGB.length; i++) {
            Output variant = compile(javaModel, bedrockIdentifier, shortId, withDye(tints, JAVA_DYE_RGB[i]));
            String texture = "textures/voxelcore/g_" + shortId + "_d" + (i + 1);
            assets.put(texture + ".png", variant.assets.get("textures/voxelcore/g_" + shortId + ".png"));
            description.getAsJsonObject("textures").addProperty("dye_" + (i + 1), texture);
        }
        JsonArray controllers = new JsonArray(); controllers.add(DYE_CONTROLLER);
        description.add("render_controllers", controllers);
        assets.put(entityPath, JSON.toJson(entity).getBytes(StandardCharsets.UTF_8));
        assets.put(DYE_CONTROLLER_PATH, dyeRenderController());
        return new Output(assets, base.cuboids, true);
    }

    /** Author-configured constant tints win; every other tint index takes the dye colour. */
    static Map<Integer, Integer> withDye(Map<Integer, Integer> tints, int rgb) {
        Map<Integer, Integer> out = new TreeMap<Integer, Integer>();
        for (int index = 0; index < 32; index++) out.put(Integer.valueOf(index), Integer.valueOf(rgb));
        out.putAll(tints);
        return out;
    }

    /**
     * @return null for genuinely flat generated item models (no 3D geometry).
     * @throws IOException if 3D geometry exists but uses features not safely translatable.
     */
    Output compile(String javaModel, String bedrockIdentifier, String shortId) throws IOException {
        return compile(javaModel, bedrockIdentifier, shortId, Collections.<Integer, Integer>emptyMap());
    }

    Output compile(String javaModel, String bedrockIdentifier, String shortId, Map<Integer, Integer> tints) throws IOException {
        JsonObject model = resolver.resolvedModel(javaModel);
        if (!model.has("elements")) return null;
        JsonArray elements = model.getAsJsonArray("elements");
        if (elements.size() == 0) return null;
        if (elements.size() > 500)
            throw new IOException("Bedrock cuboid limit exceeded for " + javaModel + ": " + elements.size());
        JsonObject textures = model.has("textures") ? model.getAsJsonObject("textures") : new JsonObject();

        boolean tinted = false;
        JsonArray cubes = new JsonArray();
        List<BufferedImage> painted = new ArrayList<BufferedImage>();
        // Animated faces: tile index -> (animation, one sampled tile per frame).
        Map<Integer, JavaTextureAnimation> animatedTiles = new TreeMap<Integer, JavaTextureAnimation>();
        Map<Integer, BufferedImage[]> frameTiles = new TreeMap<Integer, BufferedImage[]>();
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (JsonElement entry : elements) {
            JsonObject element = entry.getAsJsonObject();
            double[] from = vector(element,"from"), to = vector(element,"to");
            // One zero-length axis is a flat plane (crosses, foliage, decals); Bedrock
            // geometry accepts a 0 size there just as Java does.
            int flatAxes = 0;
            for (int a=0;a<3;a++) {
                if (!Double.isFinite(from[a]) || !Double.isFinite(to[a]))
                    throw new IOException("Invalid Java cuboid bounds in " + javaModel);
                if (to[a] == from[a]) flatAxes++;
            }
            if (flatAxes > 1) throw new IOException("Degenerate (line/point) Java cuboid in " + javaModel);
            // Geometry bounds; default face UVs below still use the authored from/to, as Java does.
            // Java also renders elements authored with to < from; Bedrock needs min/max.
            double[] lo = new double[3], hi = new double[3];
            for (int a=0;a<3;a++) { lo[a] = Math.min(from[a],to[a]); hi[a] = Math.max(from[a],to[a]); }
            if (element.has("rotation")) {
                JsonObject rotation = element.getAsJsonObject("rotation");
                double stretch = BedrockIconRenderer.rescaleFactor(rotation);
                if (stretch != 1.0) {
                    int axis = BedrockIconRenderer.singleAxis(rotation);
                    double[] origin = vector(rotation,"origin");
                    for (int a=0;a<3;a++) {
                        if (a == axis) continue;
                        double l = lo[a], h = hi[a];
                        lo[a] = origin[a] + (l-origin[a])*stretch;
                        hi[a] = origin[a] + (h-origin[a])*stretch;
                    }
                }
            }
            // Bedrock model X runs opposite to Java's (as in java2bedrock/Blockbench): mirror X,
            // otherwise asymmetric models (corner benches) render as their mirror image.
            JsonObject cube = new JsonObject();
            cube.add("origin", xyz(8-hi[0],lo[1]-8,lo[2]-8));
            cube.add("size", xyz(hi[0]-lo[0],hi[1]-lo[1],hi[2]-lo[2]));
            minX=Math.min(minX,8-hi[0]); maxX=Math.max(maxX,8-lo[0]);
            minY=Math.min(minY,lo[1]-8); maxY=Math.max(maxY,hi[1]-8);
            minZ=Math.min(minZ,lo[2]-8); maxZ=Math.max(maxZ,hi[2]-8);
            if (element.has("rotation")) {
                JsonObject rotation = element.getAsJsonObject("rotation");
                // Bedrock cubes take any x/y/z rotation; Java's legacy form is one axis.
                double[] rot = BedrockIconRenderer.angles(rotation);
                double[] pivot = rotation.has("origin") ? vector(rotation,"origin") : new double[]{8,8,8};
                cube.add("rotation",xyz(-rot[0],-rot[1],rot[2]));
                cube.add("pivot",xyz(8-pivot[0],pivot[1]-8,pivot[2]-8));
            }
            JsonObject faces = element.has("faces") ? element.getAsJsonObject("faces") : null;
            if (faces == null || faces.size()==0) throw new IOException("Cuboid without faces in "+javaModel);
            boolean[] inverted = new boolean[3];
            boolean insideOut = false;
            for (int a=0;a<3;a++) { inverted[a] = from[a] > to[a]; insideOut |= inverted[a]; }
            JsonObject mappedFaces = new JsonObject();
            for (Map.Entry<String,JsonElement> side : faces.entrySet()) {
                String direction = side.getKey();
                if (!Arrays.asList("north","south","east","west","up","down").contains(direction))
                    throw new IOException("Unsupported cuboid face " + direction + " in " + javaModel);
                JsonObject face = side.getValue().getAsJsonObject();
                int turn = face.has("rotation") ? face.get("rotation").getAsInt() : 0;
                if (turn % 90 != 0)
                    throw new IOException("Unsupported non-90-degree UV rotation in " + javaModel);
                if (!face.has("texture")) throw new IOException("Cuboid face missing texture in " + javaModel);
                String textureRef = face.get("texture").getAsString();
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(resolver.textureFor(textures,textureRef)));
                if (image==null) throw new IOException("Unreadable model texture "+textureRef);
                JavaTextureAnimation animation = resolver.animationFor(textures, textureRef);
                Integer tintIndex = face.has("tintindex") ? Integer.valueOf(face.get("tintindex").getAsInt()) : null;
                if (tintIndex != null) {
                    image = BedrockTints.face(image, tintIndex.intValue(), tints, javaModel);
                    tinted = true;
                }
                double[] uv = face.has("uv") ? vector(face,"uv") : BedrockIconRenderer.uv(direction,from,to);
                if (uv.length!=4) throw new IOException("Expected four Java UV coordinates for " + javaModel);
                // Resample the Java face UV into one full Bedrock atlas cell.
                // Java models may extend outside the 0..16 cube: clamp source UV
                // to image bounds as in the existing preview renderer. Cropping
                // the source image using Graphics.drawImage would leave these
                // oversized cuboids transparent instead.
                // Inside-out element: its face lies on the opposite side and may face inward,
                // with the texture mirrored along each reversed in-plane axis.
                String bedrockFace = insideOut ? insideOutFace(direction, inverted) : direction;
                boolean[] flip = insideOut ? insideOutFlip(direction, bedrockFace, inverted) : new boolean[2];
                BufferedImage tile = flipped(sampleTile(image, uv, turn), flip);
                int index=painted.size();
                painted.add(tile);
                if (animation != null) {
                    BufferedImage[] perFrame = new BufferedImage[animation.frames.size()];
                    for (int f = 0; f < perFrame.length; f++) {
                        BufferedImage frame = animation.frames.get(f);
                        if (tintIndex != null) frame = BedrockTints.face(frame, tintIndex.intValue(), tints, javaModel);
                        perFrame[f] = flipped(sampleTile(frame, uv, turn), flip);
                    }
                    animatedTiles.put(Integer.valueOf(index), animation);
                    frameTiles.put(Integer.valueOf(index), perFrame);
                }
                JsonObject mapped = new JsonObject();
                mapped.add("uv",xyz2(index,0)); // Absolute atlas placement fixed below.
                mapped.add("uv_size",xyz2(16,16));
                if (!insideOut) { mappedFaces.add(direction,mapped); continue; }
                // Bedrock cubes only face outward: draw the face as a zero-thickness plane at the
                // position Java puts it (the authored from/to coordinate), facing as Java's winding.
                int axis = axisOf(direction);
                boolean negative = "north".equals(direction) || "west".equals(direction) || "down".equals(direction);
                boolean atHigh = negative == inverted[axis];
                double[] planeLo = lo.clone(), planeHi = hi.clone();
                planeLo[axis] = planeHi[axis] = atHigh ? hi[axis] : lo[axis];
                JsonObject plane = cube.deepCopy();
                plane.add("origin", xyz(8-planeHi[0],planeLo[1]-8,planeLo[2]-8));
                plane.add("size", xyz(planeHi[0]-planeLo[0],planeHi[1]-planeLo[1],planeHi[2]-planeLo[2]));
                JsonObject single = new JsonObject(); single.add(bedrockFace, mapped);
                plane.add("uv", single);
                cubes.add(plane);
            }
            if (!insideOut) {
                cube.add("uv",mappedFaces);
                cubes.add(cube);
            }
        }
        if (painted.size() > 4096)
            throw new IOException("Bedrock face atlas requires too many tiles: "+painted.size());
        int columns = 1;
        while (columns*columns < painted.size()) columns*=2;
        int rows = (painted.size()+columns-1)/columns;
        int textureWidth = columns*16, textureHeight=1;
        while(textureHeight < rows*16) textureHeight*=2;
        // Animated textures: one complete atlas per animation step (frame 0 is the default
        // texture) and a per-model render controller picking one by time with a texture array,
        // the mechanism the dye controller proved live. Custom materials / uv_anim made the
        // models invisible on Bedrock (RenderDragon does not load pack materials).
        int[] steps = animationSteps(animatedTiles.values(), textureWidth, textureHeight);
        int animationFrames = steps[0], ticksPerFrame = steps[1];
        BufferedImage[] atlases = new BufferedImage[animationFrames];
        for (int frame=0;frame<animationFrames;frame++) {
            BufferedImage atlas = new BufferedImage(textureWidth,textureHeight,BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = atlas.createGraphics();
            try {
                for (int i=0;i<painted.size();i++) {
                    BufferedImage tile = painted.get(i);
                    JavaTextureAnimation animation = animatedTiles.get(Integer.valueOf(i));
                    if (animation != null) tile = frameTiles.get(Integer.valueOf(i))[animation.frameIndexAt(frame*ticksPerFrame)];
                    graphics.drawImage(tile,(i%columns)*16,(i/columns)*16,null);
                }
            } finally { graphics.dispose(); }
            atlases[frame] = atlas;
        }

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
        // Placed displays (FIXED context) render with the model's display.fixed transform, e.g.
        // block models inherit scale 0.5; without it Bedrock furniture was twice the Java size.
        JsonObject display = model.has("display") && model.get("display").isJsonObject()
                ? model.getAsJsonObject("display") : new JsonObject();
        JsonObject fixed = display.has("fixed") && display.get("fixed").isJsonObject()
                ? display.getAsJsonObject("fixed") : new JsonObject();
        double[] fixedScale = triple(fixed, "scale", new double[]{1,1,1});
        double[] fixedTranslation = triple(fixed, "translation", new double[]{0,0,0});
        double[] fixedRotation = triple(fixed, "rotation", new double[]{0,0,0});
        JsonObject bone = new JsonObject();
        bone.addProperty("name","root");
        bone.add("pivot",xyz(0,0,0));
        if (fixedRotation[0] != 0 || fixedRotation[1] != 0 || fixedRotation[2] != 0)
            bone.add("rotation", xyz(-fixedRotation[0], -fixedRotation[1], fixedRotation[2]));
        bone.add("cubes",fixedPose(cubes, fixedScale, new double[]{-fixedTranslation[0], fixedTranslation[1], fixedTranslation[2]}));
        JsonArray bones = new JsonArray(); bones.add(bone);
        JsonObject geom = new JsonObject();
        geom.add("description",description); geom.add("bones",bones);
        // Held-item geometry: the same cubes under a bone bound to the holding hand. Without
        // the binding Bedrock draws an attachable at the player's origin (at their feet).
        String heldGeometryId = geometryId + ".held";
        JsonObject heldDescription = description.deepCopy();
        heldDescription.addProperty("identifier", heldGeometryId);
        // Bone chain as in Geyser's java2bedrock converter: 'held' (hand binding + fixed base
        // pose) -> held_x -> held_y -> held_z (Java display rotation one axis per bone, since
        // Java and Bedrock compose rotations in different orders) -> 'model' with the cubes in
        // Java item space (0..16, centred on the y=8 pivot).
        JsonArray heldBones = new JsonArray();
        String parent = null;
        for (String name : new String[]{"held", "held_x", "held_y", "held_z", "model"}) {
            JsonObject heldBone = new JsonObject();
            heldBone.addProperty("name", name);
            if (parent != null) heldBone.addProperty("parent", parent);
            if (parent == null) heldBone.addProperty("binding", "q.item_slot_to_bone_name(c.item_slot)");
            heldBone.add("pivot", xyz(0, 8, 0));
            if ("model".equals(name)) heldBone.add("cubes", raised(cubes, 8));
            heldBones.add(heldBone);
            parent = name;
        }
        JsonObject heldGeom = new JsonObject();
        heldGeom.add("description",heldDescription); heldGeom.add("bones",heldBones);
        JsonArray geometries = new JsonArray(); geometries.add(geom); geometries.add(heldGeom);
        JsonObject geoRoot = new JsonObject();
        geoRoot.addProperty("format_version","1.16.0");
        geoRoot.add("minecraft:geometry",geometries);

        JsonObject attachDescription = new JsonObject();
        attachDescription.addProperty("identifier",bedrockIdentifier);
        boolean animated = animationFrames > 1;
        JsonObject materials = new JsonObject();
        materials.addProperty("default",ONE_SIDED_MATERIAL);
        materials.addProperty("enchanted","entity_alphatest_glint");
        attachDescription.add("materials",materials);
        JsonObject tex = new JsonObject();
        tex.addProperty("default","textures/voxelcore/g_"+shortId);
        tex.addProperty("enchanted","textures/misc/enchanted_item_glint");
        addFrameTextures(tex, shortId, animationFrames);
        attachDescription.add("textures",tex);
        JsonObject shape = new JsonObject();shape.addProperty("default",heldGeometryId);
        attachDescription.add("geometry",shape);
        JsonArray controllers = new JsonArray();controllers.add(animated ? animatedController(shortId) : "controller.render.item_default");
        attachDescription.add("render_controllers",controllers);
        // Java display.thirdperson_righthand / firstperson_righthand become constant bone
        // transforms so the model is hand-sized instead of a full block-scale model.
        String heldAnimation = "animation.voxelcore." + shortId + ".held";
        JsonObject attachAnimations = new JsonObject();
        attachAnimations.addProperty("third_person", heldAnimation + ".third_person");
        attachAnimations.addProperty("first_person", heldAnimation + ".first_person");
        attachDescription.add("animations", attachAnimations);
        JsonObject attachScripts = new JsonObject();
        JsonArray attachAnimate = new JsonArray();
        JsonObject third = new JsonObject(); third.addProperty("third_person", "!c.is_first_person"); attachAnimate.add(third);
        JsonObject first = new JsonObject(); first.addProperty("first_person", "c.is_first_person"); attachAnimate.add(first);
        attachScripts.add("animate", attachAnimate);
        attachDescription.add("scripts", attachScripts);
        JsonObject heldAnimations = new JsonObject();
        heldAnimations.add(heldAnimation + ".third_person", heldPose(display, "thirdperson_righthand",
                new double[]{75,45,0}, new double[]{0,2.5,0}, new double[]{0.375,0.375,0.375},
                new double[]{90,0,0}, new double[]{0,13,-3}, 1.0));
        heldAnimations.add(heldAnimation + ".first_person", heldPose(display, "firstperson_righthand",
                new double[]{0,45,0}, new double[]{0,0,0}, new double[]{0.4,0.4,0.4},
                new double[]{90,60,-40}, new double[]{4,10,4}, 1.5));
        JsonObject heldAnimationRoot = new JsonObject();
        heldAnimationRoot.addProperty("format_version", "1.8.0");
        heldAnimationRoot.add("animations", heldAnimations);
        JsonObject attach = new JsonObject(); attach.add("description",attachDescription);
        JsonObject attachRoot = new JsonObject();
        attachRoot.addProperty("format_version","1.10.0");
        attachRoot.add("minecraft:attachable",attach);

        // Java ITEM_DISPLAY has no Bedrock equivalent. Declare a separate
        // native Bedrock client entity using the same geometry and face atlas;
        // the Geyser extension must register the exact identifier at startup
        // and replace a known Paper display spawn (never every item display).
        String visualEntityId = "voxelcore:entity_" + shortId;
        JsonObject entityDescription = new JsonObject();
        entityDescription.addProperty("identifier", visualEntityId);
        JsonObject entityMaterials = new JsonObject();
        entityMaterials.addProperty("default", ONE_SIDED_MATERIAL);
        entityDescription.add("materials", entityMaterials);
        JsonObject entityTextures = new JsonObject();
        entityTextures.addProperty("default", "textures/voxelcore/g_" + shortId);
        addFrameTextures(entityTextures, shortId, animationFrames);
        entityDescription.add("textures", entityTextures);
        JsonObject entityShape = new JsonObject();
        entityShape.addProperty("default", geometryId);
        entityDescription.add("geometry", entityShape);
        JsonArray entityControllers = new JsonArray();
        entityControllers.add(animated ? animatedController(shortId) : "controller.render.default");
        entityDescription.add("render_controllers", entityControllers);
        JsonObject entityAnimations = new JsonObject();
        entityAnimations.addProperty("voxelcore_transform", TRANSFORM_ANIMATION);
        entityDescription.add("animations", entityAnimations);
        JsonObject scripts = new JsonObject();
        JsonArray animate = new JsonArray(); animate.add("voxelcore_transform");
        scripts.add("animate", animate);
        entityDescription.add("scripts", scripts);
        JsonObject clientEntity = new JsonObject();
        clientEntity.add("description", entityDescription);
        JsonObject clientEntityRoot = new JsonObject();
        clientEntityRoot.addProperty("format_version", "1.10.0");
        clientEntityRoot.add("minecraft:client_entity", clientEntity);

        Map<String,byte[]> assets = new TreeMap<String,byte[]>();
        assets.put("models/entity/voxelcore/"+shortId+".geo.json",JSON.toJson(geoRoot).getBytes(StandardCharsets.UTF_8));
        assets.put("attachables/"+shortId+".json",JSON.toJson(attachRoot).getBytes(StandardCharsets.UTF_8));
        assets.put("animations/voxelcore_held_"+shortId+".animation.json",
                JSON.toJson(heldAnimationRoot).getBytes(StandardCharsets.UTF_8));
        assets.put("entity/voxelcore/"+shortId+".entity.json",
                JSON.toJson(clientEntityRoot).getBytes(StandardCharsets.UTF_8));
        for (int frame=0;frame<animationFrames;frame++) {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            if (!ImageIO.write(atlases[frame],"png",png)) throw new IOException("PNG encoder missing");
            assets.put(frameTexture(shortId, frame)+".png",png.toByteArray());
        }
        assets.put(TRANSFORM_ANIMATION_PATH, transformAnimation());
        if (animated)
            assets.put("render_controllers/"+shortId+".render_controllers.json",
                    animatedRenderController(shortId, animationFrames, ticksPerFrame));
        return new Output(assets,cubes.size(),tinted);
    }

    /** Resample a Java face UV region (with UV rotation) into one 16x16 atlas tile. */
    private static BufferedImage sampleTile(BufferedImage image, double[] uv, int turn) {
        // Java models may extend outside the 0..16 cube: clamp source UV to image bounds as in
        // the preview renderer; cropping with drawImage would leave oversized cuboids transparent.
        BufferedImage tile = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for (int ty=0;ty<16;ty++) for (int tx=0;tx<16;tx++) {
            double u=(tx+0.5)/16.0, v=(ty+0.5)/16.0;
            switch ((turn % 360 + 360) % 360) {
                case 90: { double t=u; u=v; v=1-t; break; }
                case 180: u=1-u; v=1-v; break;
                case 270: { double t=u; u=1-v; v=t; break; }
                default: break;
            }
            double sx=uv[0]+u*(uv[2]-uv[0]), sy=uv[1]+v*(uv[3]-uv[1]);
            int pixelX=Math.min(image.getWidth()-1,Math.max(0,(int)Math.floor(sx*image.getWidth()/16.0)));
            int pixelY=Math.min(image.getHeight()-1,Math.max(0,(int)Math.floor(sy*image.getHeight()/16.0)));
            tile.setRGB(tx,ty,image.getRGB(pixelX,pixelY));
        }
        return tile;
    }

    /**
     * Java draws every model face one-sided (back faces culled); vanilla entity_alphatest is
     * double-sided, which showed the outside of inside-out elements (staff mane, help box).
     */
    static final String ONE_SIDED_MATERIAL = "entity_alphatest_one_sided";

    static int axisOf(String direction) {
        if ("east".equals(direction) || "west".equals(direction)) return 0;
        if ("up".equals(direction) || "down".equals(direction)) return 1;
        return 2;
    }

    static String opposite(String direction) {
        switch (direction) {
            case "north": return "south";
            case "south": return "north";
            case "east": return "west";
            case "west": return "east";
            case "up": return "down";
            default: return "up";
        }
    }

    /** Texture u and v world axes of a face. */
    private static int[] faceAxes(String direction) {
        switch (axisOf(direction)) {
            case 0: return new int[]{2, 1};
            case 1: return new int[]{0, 2};
            default: return new int[]{0, 1};
        }
    }

    /**
     * World direction (+1/-1 along the axis) in which a face's texture u / v increase on a normal
     * Java cube, from Java's default face UVs (e.g. north u = 16 - x, south u = x, down v = 16 - z).
     */
    private static int[] faceSigns(String direction) {
        switch (direction) {
            case "north": return new int[]{-1, -1};
            case "south": return new int[]{1, -1};
            case "east": return new int[]{-1, -1};
            case "west": return new int[]{1, -1};
            case "up": return new int[]{1, 1};
            default: return new int[]{1, -1};
        }
    }

    /**
     * Java builds face vertices from the authored from/to, so reversing one in-plane axis
     * reverses the winding: the face then shows its other side. An odd count turns it inward.
     */
    static String insideOutFace(String direction, boolean[] inverted) {
        int[] axes = faceAxes(direction);
        return inverted[axes[0]] ^ inverted[axes[1]] ? opposite(direction) : direction;
    }

    /** Tile {flipU, flipV} so the Bedrock face's texture runs the way Java's mirrored face does. */
    static boolean[] insideOutFlip(String direction, String bedrockFace, boolean[] inverted) {
        int[] axes = faceAxes(direction), java = faceSigns(direction), bedrock = faceSigns(bedrockFace);
        boolean[] flip = new boolean[2];
        for (int i = 0; i < 2; i++) flip[i] = (inverted[axes[i]] ? -java[i] : java[i]) != bedrock[i];
        return flip;
    }

    private static BufferedImage flipped(BufferedImage tile, boolean[] flip) {
        if (!flip[0] && !flip[1]) return tile;
        BufferedImage out = new BufferedImage(tile.getWidth(), tile.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < tile.getHeight(); y++) for (int x = 0; x < tile.getWidth(); x++)
            out.setRGB(x, y, tile.getRGB(flip[0] ? tile.getWidth()-1-x : x, flip[1] ? tile.getHeight()-1-y : y));
        return out;
    }

    /** Most atlas copies an animated model may stack, and the pixel budget for the whole strip. */
    static final int MAX_ANIMATION_FRAMES = 64;
    static final long MAX_ANIMATED_ATLAS_PIXELS = 16L * 1024 * 1024;

    /**
     * {frames, ticksPerFrame} covering one full cycle of every animation on the model: the
     * least common multiple of their periods, sampled every ticksPerFrame ticks so the strip
     * stays within MAX_ANIMATION_FRAMES and the pixel budget. {1, 1} when nothing animates.
     */
    static int[] animationSteps(java.util.Collection<JavaTextureAnimation> animations, int width, int height) {
        if (animations.isEmpty()) return new int[]{1, 1};
        long period = 1, unit = 0;
        for (JavaTextureAnimation animation : new java.util.LinkedHashSet<JavaTextureAnimation>(animations)) {
            period = lcm(period, animation.period);
            for (int tick : animation.ticks) unit = gcd(unit, tick);
            if (period > 100_000) { period = 100_000; break; }
        }
        long budget = Math.max(1, MAX_ANIMATED_ATLAS_PIXELS / Math.max(1L, (long) width * height));
        int maxFrames = (int) Math.max(1, Math.min(MAX_ANIMATION_FRAMES, budget));
        // No frame changes between multiples of the shortest common frame duration.
        long step = Math.max(1, unit);
        while ((period + step - 1) / step > maxFrames) step += Math.max(1, unit);
        int ticksPerFrame = (int) step;
        int frames = (int) Math.max(1, (period + ticksPerFrame - 1) / ticksPerFrame);
        return new int[]{frames, ticksPerFrame};
    }

    private static long lcm(long a, long b) {
        return a / gcd(a, b) * b;
    }

    private static long gcd(long a, long b) {
        while (b != 0) { long t = a % b; a = b; b = t; }
        return Math.abs(a);
    }

    /** Atlas texture of one animation frame; frame 0 is the model's default texture. */
    static String frameTexture(String shortId, int frame) {
        return "textures/voxelcore/g_" + shortId + (frame == 0 ? "" : "_f" + frame);
    }

    private static void addFrameTextures(JsonObject textures, String shortId, int frames) {
        for (int frame = 1; frame < frames; frame++) textures.addProperty("frame_" + frame, frameTexture(shortId, frame));
    }

    static String animatedController(String shortId) {
        return "controller.render.voxelcore.animated_" + shortId;
    }

    /**
     * Render controller showing frame floor(age in ticks / ticksPerFrame) mod frames, chosen from
     * a texture array like vanilla variant textures. Vanilla materials only: Bedrock ignores
     * pack-defined materials, so uv_anim with a custom material rendered nothing.
     */
    static byte[] animatedRenderController(String shortId, int frames, int ticksPerFrame) {
        JsonArray textures = new JsonArray();
        textures.add("Texture.default");
        for (int frame = 1; frame < frames; frame++) textures.add("Texture.frame_" + frame);
        JsonObject arrays = new JsonObject(), textureArrays = new JsonObject();
        textureArrays.add("Array.frames", textures);
        arrays.add("textures", textureArrays);
        JsonObject controller = new JsonObject();
        controller.add("arrays", arrays);
        controller.addProperty("geometry", "Geometry.default");
        JsonArray materials = new JsonArray();
        JsonObject all = new JsonObject(); all.addProperty("*", "Material.default"); materials.add(all);
        controller.add("materials", materials);
        JsonArray selected = new JsonArray();
        selected.add("Array.frames[math.mod(math.floor(q.life_time * " + (20.0 / ticksPerFrame) + "), " + frames + ")]");
        controller.add("textures", selected);
        JsonObject controllers = new JsonObject(); controllers.add(animatedController(shortId), controller);
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.8.0");
        root.add("render_controllers", controllers);
        return JSON.toJson(root).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Copy of the (centred) cubes with a Java display transform's scale and translation baked
     * in, about the model centre: p' = p * scale + translation. Per-face UVs are absolute
     * atlas cells, so scaling a cube never stretches its texture.
     */
    private static JsonArray fixedPose(JsonArray cubes, double[] scale, double[] translation) {
        if (scale[0] == 1 && scale[1] == 1 && scale[2] == 1
                && translation[0] == 0 && translation[1] == 0 && translation[2] == 0) return cubes;
        JsonArray out = cubes.deepCopy();
        for (JsonElement element : out) {
            JsonObject cube = element.getAsJsonObject();
            for (String field : new String[]{"origin", "pivot"}) {
                if (!cube.has(field)) continue;
                JsonArray point = cube.getAsJsonArray(field);
                for (int a = 0; a < 3; a++)
                    point.set(a, new com.google.gson.JsonPrimitive(point.get(a).getAsDouble() * scale[a] + translation[a]));
            }
            JsonArray size = cube.getAsJsonArray("size");
            for (int a = 0; a < 3; a++)
                size.set(a, new com.google.gson.JsonPrimitive(size.get(a).getAsDouble() * Math.abs(scale[a])));
        }
        return out;
    }

    /** Copy of the cubes moved up (Java item space keeps y in 0..16; the entity geometry is centred). */
    private static JsonArray raised(JsonArray cubes, double dy) {
        JsonArray out = cubes.deepCopy();
        for (JsonElement element : out) {
            JsonObject cube = element.getAsJsonObject();
            for (String field : new String[]{"origin", "pivot"}) {
                if (!cube.has(field)) continue;
                JsonArray point = cube.getAsJsonArray(field);
                point.set(1, new com.google.gson.JsonPrimitive(point.get(1).getAsDouble() + dy));
            }
        }
        return out;
    }

    /**
     * Constant looping pose from a Java display transform (vanilla block defaults when absent):
     * the hand base pose on 'held', then Java rotation per axis (x, y negated for Bedrock),
     * translation (x negated) and scale on held_x.
     */
    private static JsonObject heldPose(JsonObject display, String key, double[] rotation, double[] translation, double[] scale,
                                       double[] baseRotation, double[] basePosition, double baseScale) {
        if (display.has(key) && display.get(key).isJsonObject()) {
            JsonObject transform = display.getAsJsonObject(key);
            rotation = triple(transform, "rotation", new double[]{0,0,0});
            translation = triple(transform, "translation", new double[]{0,0,0});
            scale = triple(transform, "scale", new double[]{1,1,1});
        }
        JsonObject bones = new JsonObject();
        JsonObject base = new JsonObject();
        base.add("rotation", xyz(baseRotation[0], baseRotation[1], baseRotation[2]));
        base.add("position", xyz(basePosition[0], basePosition[1], basePosition[2]));
        if (baseScale != 1.0) base.add("scale", xyz(baseScale, baseScale, baseScale));
        bones.add("held", base);
        JsonObject x = new JsonObject();
        x.add("rotation", xyz(-rotation[0], 0, 0));
        x.add("position", xyz(-translation[0], translation[1], translation[2]));
        x.add("scale", xyz(scale[0], scale[1], scale[2]));
        bones.add("held_x", x);
        JsonObject y = new JsonObject(); y.add("rotation", xyz(0, -rotation[1], 0)); bones.add("held_y", y);
        JsonObject z = new JsonObject(); z.add("rotation", xyz(0, 0, rotation[2])); bones.add("held_z", z);
        JsonObject animation = new JsonObject();
        animation.addProperty("loop", true);
        animation.add("bones", bones);
        return animation;
    }

    private static double[] triple(JsonObject object, String field, double[] fallback) {
        if (!object.has(field) || !object.get(field).isJsonArray() || object.getAsJsonArray(field).size() != 3) return fallback;
        JsonArray values = object.getAsJsonArray(field);
        return new double[]{values.get(0).getAsDouble(), values.get(1).getAsDouble(), values.get(2).getAsDouble()};
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
