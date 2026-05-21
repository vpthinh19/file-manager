package com.vpt.filemanager.error;

public class FileSystemException extends AppException {
    public FileSystemException(String message) {
        super(message);
    }

    public FileSystemException(String message, Throwable cause) {
        super(message, cause);
    }
}

