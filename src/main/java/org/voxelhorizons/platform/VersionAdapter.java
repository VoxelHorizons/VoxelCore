package org.voxelhorizons.platform;

import org.voxelhorizons.platform.item.ItemPlatformAdapter;

public interface VersionAdapter {

    Version version();

    Capabilities capabilities();

    ItemPlatformAdapter items();
}