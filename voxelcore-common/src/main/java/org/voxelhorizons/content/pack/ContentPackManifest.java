package org.voxelhorizons.content.pack;

import org.voxelhorizons.content.ContentID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ContentPackManifest {
    private final int schema;
    private final String namespace;
    private final List<String> dependencies;

    public ContentPackManifest(int schema, String namespace) {
        this(schema, namespace, Collections.<String>emptyList());
    }

    public ContentPackManifest(int schema, String namespace, List<String> dependencies) {
        if (schema != 1) {
            throw new IllegalArgumentException("Unsupported content pack schema: " + schema);
        }
        this.schema = schema;
        this.namespace = ContentID.of(namespace, "validation").namespace();

        Set<String> normalized = new LinkedHashSet<String>();
        if (dependencies != null) {
            for (String dependency : dependencies) {
                String value = ContentID.of(dependency, "validation").namespace();
                if (value.equals(this.namespace)) {
                    throw new IllegalArgumentException("Content pack cannot depend on itself: " + value);
                }
                if (!normalized.add(value)) {
                    throw new IllegalArgumentException("Duplicate content pack dependency: " + value);
                }
            }
        }
        this.dependencies = Collections.unmodifiableList(new ArrayList<String>(normalized));
    }

    public int schema() {
        return schema;
    }

    public String namespace() {
        return namespace;
    }

    public List<String> dependencies() {
        return dependencies;
    }

    public boolean dependsOn(String namespace) {
        return dependencies.contains(ContentID.of(namespace, "validation").namespace());
    }
}
