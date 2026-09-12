package org.voxelhorizons.content;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ContentID {

    private static final Pattern VALID = Pattern.compile("[a-z0-9._-]+");

    private final String namespace;
    private final String value;

    public ContentID(String namespace, String value) {
        this.namespace = normalize(namespace);
        this.value = normalize(value);

        if (!VALID.matcher(this.namespace).matches()) {
            throw new IllegalArgumentException("Invalid content namespace: " + this.namespace);
        }

        if (!VALID.matcher(this.value).matches()) {
            throw new IllegalArgumentException("Invalid content id: " + this.value);
        }
    }

    public static ContentID of(String namespace, String value) {
        return new ContentID(namespace, value);
    }

    public static ContentID parse(String input, String defaultNamespace) {
        Objects.requireNonNull(input, "input");

        int separator = input.indexOf(':');
        if (separator < 0) {
            return new ContentID(defaultNamespace, input);
        }

        return new ContentID(input.substring(0, separator), input.substring(separator + 1));
    }

    private static String normalize(String value) {
        return Objects.requireNonNull(value, "value").trim().toLowerCase(Locale.ROOT);
    }

    public String namespace() {
        return namespace;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ContentID)) return false;
        ContentID other = (ContentID) object;
        return namespace.equals(other.namespace) && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, value);
    }

    @Override
    public String toString() {
        return namespace + ":" + value;
    }
}
