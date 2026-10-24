package org.voxelhorizons.geyser;

import org.geysermc.event.subscribe.Subscribe;
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
