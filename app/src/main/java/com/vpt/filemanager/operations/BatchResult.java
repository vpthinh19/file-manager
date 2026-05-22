package com.vpt.filemanager.operations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Generic result for batch operations that may continue after per-item failures.
 */
public final class BatchResult {
    public final int ok;
    public final int failed;
    @Nullable public final String lastError;

    public BatchResult(int ok, int failed, @Nullable String lastError) {
        this.ok = ok;
        this.failed = failed;
        this.lastError = lastError;
    }

    @NonNull
    public String message(@NonNull String verb) {
        if (failed == 0) {
            return ok + " " + verb;
        }
        if (ok == 0) {
            return verb.substring(0, 1).toUpperCase() + verb.substring(1)
                    + " failed: " + (lastError == null ? "unknown" : lastError);
        }
        return ok + " " + verb + ", " + failed + " failed: "
                + (lastError == null ? "unknown" : lastError);
    }
}
