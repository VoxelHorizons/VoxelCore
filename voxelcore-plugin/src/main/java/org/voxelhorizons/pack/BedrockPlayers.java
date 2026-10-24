package org.voxelhorizons.pack;

import java.util.UUID;

/** Optional public APIs only; no player-name/UUID heuristics and no hard dependency. */
public final class BedrockPlayers {
    private BedrockPlayers() { }
    public static boolean isBedrock(UUID id) {
        return query("org.geysermc.floodgate.api.FloodgateApi", "getInstance", "isFloodgatePlayer", id)
                || query("org.geysermc.geyser.api.GeyserApi", "api", "isBedrockPlayer", id);
    }
    private static boolean query(String name, String accessor, String method, UUID id) {
        try {
            Class<?> api = Class.forName(name);
            Object instance = api.getMethod(accessor).invoke(null);
            return instance != null && Boolean.TRUE.equals(api.getMethod(method, UUID.class).invoke(instance, id));
        } catch (ReflectiveOperationException | LinkageError unavailable) { return false; }
    }
}
