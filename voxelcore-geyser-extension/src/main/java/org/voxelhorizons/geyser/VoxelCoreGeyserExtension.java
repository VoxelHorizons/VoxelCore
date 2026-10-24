package org.voxelhorizons.geyser;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.entity.custom.CustomEntityDefinition;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent;
import org.geysermc.geyser.api.util.Identifier;
import java.util.Map;
import java.util.TreeSet;
import java.io.IOException;
import java.util.Set;
import org.geysermc.geyser.api.command.Command;
import org.geysermc.geyser.api.command.CommandSource;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCommandsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.extension.Extension;

/**
 * Geyser-side companion: audits the local VoxelCore pack publication *before*
 * players are admitted. It does not register the pack a second time because
 * Geyser already scans its packs folder. Nor does a matching on-disk receipt
 * prove that a running Geyser loaded updated mappings without a restart.
 */
public final class VoxelCoreGeyserExtension implements Extension {
    @Subscribe
    public void onDefineResourcePacks(GeyserDefineResourcePacksEvent event) {
        report("resource pack registration phase");
        logger().info("Geyser currently has " + event.resourcePacks().size()
                + " registered packs; VoxelCore uses the normal packs/ scan.");
    }

    private volatile Map<String,String> nativeEntityIdentifiers = java.util.Collections.emptyMap();

    @Subscribe
    public void onDefineEntities(GeyserDefineEntitiesEvent event) {
        PublicationVerifier.State state = PublicationVerifier.inspect(geyserApi().configDirectory());
        if (state.status()!=PublicationVerifier.Status.VERIFIED_ON_DISK) {
            logger().warning("VoxelCore native entity types not registered: publication is " + state.status());
            return;
        }
        Map<String,String> catalog;
        try {
            catalog=EntityDefinitionCatalog.load(geyserApi().configDirectory());
        } catch (IOException invalid) {
            logger().warning("VoxelCore native entities not registered: " + invalid.getMessage());
            return;
        }
        Set<String> types = new TreeSet<String>(catalog.values());
        for(String identifier:types) {
            try {
                event.register(CustomEntityDefinition.of(Identifier.of(identifier)));
            } catch (RuntimeException incompatible) {
                logger().warning("Could not register VoxelCore entity " + identifier + ": " + incompatible.getMessage());
                // A partial registration must not enable an incorrect model lookup.
                nativeEntityIdentifiers=java.util.Collections.emptyMap();
                return;
            }
        }
        nativeEntityIdentifiers = catalog;
        logger().info("Registered " + types.size() + " VoxelCore Bedrock client entity definitions from "
                + state.revision() + " (spawn replacement/state synchronization still required).");
    }

    @Subscribe
    public void onDefineCommands(GeyserDefineCommandsEvent event) {
        event.register(Command.builder(this)
                .name("status")
                .description("Inspect VoxelCore's Geyser pack and mapping revision on disk")
                .source(CommandSource.class)
                .playerOnly(false)
                .suggestedOpOnly(true)
                .permission("voxelcore.geyser.status")
                .executor((source, command, args) -> {
                    PublicationVerifier.State state = PublicationVerifier.inspect(geyserApi().configDirectory());
                    source.sendMessage("VoxelCore Bedrock: " + state.status()
                            + (state.revision().isEmpty() ? "" : " revision=" + state.revision())
                            + " (" + state.details() + ")");
                    source.sendMessage("Note: this verifies on-disk artifacts, not each client's active resources.");
                })
                .build());
    }

    @Subscribe
    public void onPostInitialize(GeyserPostInitializeEvent event) {
        report("post-initialize");
    }

    private void report(String phase) {
        PublicationVerifier.State state = PublicationVerifier.inspect(geyserApi().configDirectory());
        if (state.status() == PublicationVerifier.Status.VERIFIED_ON_DISK) {
            logger().info("VoxelCore " + phase + " deployment " + state.revision()
                    + " verified on disk. This is NOT proof that live Bedrock sessions have loaded it.");
        } else if (state.status() == PublicationVerifier.Status.NOT_PUBLISHED) {
            logger().warning("No VoxelCore Bedrock resource pack deployment found in "
                    + geyserApi().configDirectory() + ". Build/publish from Paper first.");
        } else {
            logger().warning("VoxelCore " + phase + " publication " + state.status()
                    + ": " + state.details() + ". Do not accept mismatched pack/mappings.");
        }
    }
}
