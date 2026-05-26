package com.vpt.filemanager.core.error;

public class ArchiveOperationException extends FileOperationException {
    public ArchiveOperationException(String message) {
        super(message);
    }

    public ArchiveOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
