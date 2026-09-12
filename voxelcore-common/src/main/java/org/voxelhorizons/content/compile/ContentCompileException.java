package org.voxelhorizons.content.compile;

public final class ContentCompileException extends RuntimeException {
    public ContentCompileException(String message) {
        super(message);
    }

    public ContentCompileException(String message, Throwable cause) {
        super(message, cause);
    }
}
