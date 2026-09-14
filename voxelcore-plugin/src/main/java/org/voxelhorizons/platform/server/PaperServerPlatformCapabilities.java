package org.voxelhorizons.platform.server;

final class PaperServerPlatformCapabilities implements ServerPlatformCapabilities {
    @Override public String platformName() { return "Paper"; }
    @Override public boolean paperApiAvailable() { return true; }
}
