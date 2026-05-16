package com.vpt.filemanager.core.error;

public final class ArchiveException extends FileSystemException {
    public ArchiveException(String message) {
        super(message);
    }

    public ArchiveException(String message, Throwable cause) {
        super(message, cause);
    }
}

