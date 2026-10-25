package org.voxelhorizons.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A Java animated texture: the PNG holds every frame (a vertical strip, or a grid when
 * animation.width/height say so) and the .png.mcmeta "animation" section lists the frame order
 * and timing in ticks. Bedrock needs the frames separately: icons use the first frame, 3D models
 * flip through atlas copies with a render controller uv_anim (live test: the whole strip was
 * squeezed onto each face).
 */
final class JavaTextureAnimation {
    final List<BufferedImage> frames;
    final int[] sequence, ticks;
    final int period;

    private JavaTextureAnimation(List<BufferedImage> frames, int[] sequence, int[] ticks) {
        this.frames = frames;
        this.sequence = sequence;
        this.ticks = ticks;
        int total = 0;
        for (int tick : ticks) total += tick;
        this.period = Math.max(1, total);
    }

    /** @return null when the texture is not animated (no animation section, or a single frame) */
    static JavaTextureAnimation read(byte[] png, byte[] mcmeta, String name) throws IOException {
        if (png == null || mcmeta == null) return null;
        JsonObject animation;
        try {
            JsonObject root = JsonParser.parseString(new String(mcmeta, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("animation") || !root.get("animation").isJsonObject()) return null;
            animation = root.getAsJsonObject("animation");
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid texture animation metadata " + name + ": " + invalid.getMessage());
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        if (image == null) throw new IOException("Unreadable animated texture " + name);
        int width = image.getWidth(), height = image.getHeight();
        // Java: frame size from width/height when given, otherwise squares of the shorter side.
        int side = Math.min(width, height);
        int frameWidth = animation.has("width") ? animation.get("width").getAsInt() : side;
        int frameHeight = animation.has("height") ? animation.get("height").getAsInt() : side;
        if (animation.has("width") && !animation.has("height")) frameHeight = Math.min(frameWidth, height);
        if (animation.has("height") && !animation.has("width")) frameWidth = Math.min(frameHeight, width);
        if (frameWidth < 1 || frameHeight < 1 || width % frameWidth != 0 || height % frameHeight != 0)
            throw new IOException("Animated texture " + name + " is not a whole number of " + frameWidth + "x" + frameHeight + " frames");
        int columns = width / frameWidth, count = columns * (height / frameHeight);
        if (count < 2) return null;
        List<BufferedImage> frames = new ArrayList<BufferedImage>();
        for (int i = 0; i < count; i++)
            frames.add(image.getSubimage((i % columns) * frameWidth, (i / columns) * frameHeight, frameWidth, frameHeight));
        int frametime = animation.has("frametime") ? Math.max(1, animation.get("frametime").getAsInt()) : 1;
        List<int[]> order = new ArrayList<int[]>();
        if (animation.has("frames") && animation.get("frames").isJsonArray()) {
            for (JsonElement entry : animation.getAsJsonArray("frames")) {
                int index, time = frametime;
                if (entry.isJsonObject()) {
                    index = entry.getAsJsonObject().get("index").getAsInt();
                    if (entry.getAsJsonObject().has("time")) time = Math.max(1, entry.getAsJsonObject().get("time").getAsInt());
                } else index = entry.getAsInt();
                if (index < 0 || index >= count) throw new IOException("Animated texture " + name + " references missing frame " + index);
                order.add(new int[]{index, time});
            }
        } else for (int i = 0; i < count; i++) order.add(new int[]{i, frametime});
        if (order.isEmpty()) return null;
        int[] sequence = new int[order.size()], ticks = new int[order.size()];
        for (int i = 0; i < order.size(); i++) { sequence[i] = order.get(i)[0]; ticks[i] = order.get(i)[1]; }
        return new JavaTextureAnimation(frames, sequence, ticks);
    }

    BufferedImage first() { return frames.get(sequence[0]); }

    /** The frame Java shows at this game tick (interpolation is not reproduced). */
    BufferedImage at(int tick) { return frames.get(frameIndexAt(tick)); }

    /** Index into {@link #frames} shown at this game tick. */
    int frameIndexAt(int tick) {
        int t = Math.floorMod(tick, period);
        for (int i = 0; i < sequence.length; i++) {
            if (t < ticks[i]) return sequence[i];
            t -= ticks[i];
        }
        return sequence[sequence.length - 1];
    }
}
