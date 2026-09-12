package org.voxelhorizons.content.load;

public final class ContentLoadException extends RuntimeException {
    public ContentLoadException(String message) {
        super(message);
    }

    public ContentLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
