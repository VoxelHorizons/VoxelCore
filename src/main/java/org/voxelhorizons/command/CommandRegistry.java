package org.voxelhorizons.command;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class CommandRegistry {
    private static final Set<CommandFactory> factories = new HashSet<>();

    private CommandRegistry() {}

    public static void register(CommandFactory factory) throws Exception {
        if(!factories.contains(factory)) {
            factories.add(factory);
        } else {
            throw new Exception("Factory was attempted to be registered more than once. This is a bug, please fix.");
        }
    }

    public static Set<CommandFactory> getFactories() {
        return Collections.unmodifiableSet(factories);
    }
}
