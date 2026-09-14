package org.voxelhorizons.platform.server;

/**
 * Runtime server-software capabilities independent of the Minecraft version adapter.
 * Implementations must only expose APIs that are actually available on the running server.
 */
public interface ServerPlatformCapabilities {
    String platformName();
    boolean paperApiAvailable();
}
