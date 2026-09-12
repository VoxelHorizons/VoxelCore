package org.voxelhorizons.content;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record ContentID(String namespace, String value) {

    private static final Pattern VALID =
            Pattern.compile("[a-z0-9._-]+");

    public ContentID {
        namespace = normalize(namespace);
        value = normalize(value);

        if (!VALID.matcher(namespace).matches()) {
            throw new IllegalArgumentException(
                    "Invalid content namespace: " + namespace
            );
        }

        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Invalid content id: " + value
            );
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

        return new ContentID(
                input.substring(0, separator),
                input.substring(separator + 1)
        );
    }

    private static String normalize(String value) {
        return Objects.requireNonNull(value)
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return namespace + ":" + value;
    }
}