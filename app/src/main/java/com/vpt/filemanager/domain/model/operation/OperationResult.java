package com.vpt.filemanager.domain.model.operation;

public final class OperationResult {
    private final int affectedEntries;
    private final long affectedBytes;

    public OperationResult(int affectedEntries, long affectedBytes) {
        this.affectedEntries = affectedEntries;
        this.affectedBytes = affectedBytes;
    }

    public int affectedEntries() {
        return affectedEntries;
    }

    public long affectedBytes() {
        return affectedBytes;
    }
}

