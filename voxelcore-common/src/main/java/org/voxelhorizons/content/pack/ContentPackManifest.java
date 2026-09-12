package org.voxelhorizons.content.pack;

import org.voxelhorizons.content.ContentID;

public final class ContentPackManifest {
    private final int schema;
    private final String namespace;

    public ContentPackManifest(int schema, String namespace) {
        if (schema != 1) {
            throw new IllegalArgumentException("Unsupported content pack schema: " + schema);
        }
        this.schema = schema;
        this.namespace = ContentID.of(namespace, "validation").namespace();
    }

    public int schema() {
        return schema;
    }

    public String namespace() {
        return namespace;
    }
}
