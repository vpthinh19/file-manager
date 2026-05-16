package com.vpt.filemanager.domain.model.operation;

public final class OperationProgress {
    private final long completedBytes;
    private final long totalBytes;
    private final String message;

    public OperationProgress(long completedBytes, long totalBytes, String message) {
        this.completedBytes = completedBytes;
        this.totalBytes = totalBytes;
        this.message = message;
    }

    public long completedBytes() {
        return completedBytes;
    }

    public long totalBytes() {
        return totalBytes;
    }

    public String message() {
        return message;
    }
}

