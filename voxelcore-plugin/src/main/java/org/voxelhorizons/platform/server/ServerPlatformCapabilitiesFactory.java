package org.voxelhorizons.platform.server;

import org.bukkit.Server;

public final class ServerPlatformCapabilitiesFactory {
    private static final String[] PAPER_MARKERS = {
            "io.papermc.paper.ServerBuildInfo",
            "io.papermc.paper.configuration.GlobalConfiguration",
            "com.destroystokyo.paper.PaperConfig"
    };

    private ServerPlatformCapabilitiesFactory() {}

    public static ServerPlatformCapabilities create(Server server) {
        if (server == null) throw new IllegalArgumentException("server cannot be null");
        return create(server.getName(), new ClassAvailability() {
            @Override public boolean isAvailable(String className) {
                try {
                    Class.forName(className, false, ServerPlatformCapabilitiesFactory.class.getClassLoader());
                    return true;
                } catch (ClassNotFoundException ignored) {
                    return false;
                } catch (LinkageError ignored) {
                    return false;
                }
            }
        });
    }

    static ServerPlatformCapabilities create(String serverName, ClassAvailability classes) {
        if (classes == null) throw new IllegalArgumentException("classes cannot be null");
        if (serverName != null && "paper".equals(serverName.trim().toLowerCase(java.util.Locale.ROOT))) {
            return new PaperServerPlatformCapabilities();
        }
        for (String marker : PAPER_MARKERS) {
            if (classes.isAvailable(marker)) return new PaperServerPlatformCapabilities();
        }
        return new BukkitServerPlatformCapabilities();
    }

    interface ClassAvailability {
        boolean isAvailable(String className);
    }
}
