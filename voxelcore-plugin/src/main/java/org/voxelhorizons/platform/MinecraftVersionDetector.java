package org.voxelhorizons.platform;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves the game version without confusing it with the Bukkit API version. */
final class MinecraftVersionDetector {
    private static final Pattern VERSION_AT_START = Pattern.compile("^\\s*(\\d+(?:\\.\\d+){1,2})\\b");
    private static final Pattern MINECRAFT_MARKER = Pattern.compile("\\(MC:\\s*(\\d+(?:\\.\\d+){1,2})\\s*\\)");

    private MinecraftVersionDetector() {}

    static String detect() {
        return detect(paperMinecraftVersion(), Bukkit.getVersion(), Bukkit.getBukkitVersion());
    }

    /** Exposed package-locally for deterministic tests of version-source priority. */
    static String detect(String gameVersion, String implementationVersion, String bukkitVersion) {
        String detected = versionAtStart(gameVersion);
        if (detected != null) return detected;

        Matcher marker = MINECRAFT_MARKER.matcher(implementationVersion == null ? "" : implementationVersion);
        if (marker.find()) return marker.group(1);

        detected = versionAtStart(bukkitVersion);
        if (detected != null) return detected;

        throw new IllegalStateException("Unable to determine the Minecraft game version from the server APIs");
    }

    private static String paperMinecraftVersion() {
        try {
            // Paper exposes the game version separately. Reflect so the plugin still loads on
            // older Bukkit APIs where this method is not present.
            Method method = Bukkit.class.getMethod("getMinecraftVersion");
            Object value = method.invoke(null);
            return value instanceof String ? (String) value : null;
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return null;
        }
    }

    private static String versionAtStart(String value) {
        Matcher matcher = VERSION_AT_START.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group(1) : null;
    }
}
