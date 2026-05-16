package com.vpt.filemanager.core.concurrent;

public interface ProgressReporter {
    ProgressReporter NONE = (completedBytes, totalBytes, message) -> { };

    void onProgress(long completedBytes, long totalBytes, String message);
}

