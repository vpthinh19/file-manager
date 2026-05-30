package com.vpt.filemanager.core.error;

/** Signals that an encrypted archive operation must be retried with a passphrase. */
public final class ArchivePasswordRequiredException extends ArchiveOperationException {
    public ArchivePasswordRequiredException(String message) {
        super(message);
    }

    public ArchivePasswordRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
