package org.voxelhorizons.platform.v1_12;

import org.voxelhorizons.platform.Capabilities;
import org.voxelhorizons.platform.Version;
import org.voxelhorizons.platform.VersionAdapter;
import org.voxelhorizons.platform.item.ItemPlatformAdapter;

public final class v1_12_VersionAdapter implements VersionAdapter {

    private final Version version;
    private final ItemPlatformAdapter itemAdapter;

    public v1_12_VersionAdapter(Version version) {
        this.version = version;
        this.itemAdapter = new v1_12_ItemAdapter();
    }

    @Override
    public Version version() {
        return version;
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(false, false, false, false, false, false);
    }

    @Override
    public ItemPlatformAdapter items() {
        return itemAdapter;
    }
}
