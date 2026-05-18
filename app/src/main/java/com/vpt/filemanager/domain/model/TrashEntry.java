package com.vpt.filemanager.domain.model;

import androidx.annotation.NonNull;

/**
 * One entry inside the user's trash. Pure data — produced by {@code TrashRepository#entries} and
 * consumed by the Trash UI to show restoreable items.
 */
public final class TrashEntry {
    @NonNull public final String id;
    @NonNull public final String originalPath;
    @NonNull public final String displayName;
    @NonNull public final String trashPath;
    public final long deletedAtMillis;
    public final long sizeBytes;
    public final boolean directory;

    public TrashEntry(
            @NonNull String id,
            @NonNull String originalPath,
            @NonNull String displayName,
            @NonNull String trashPath,
            long deletedAtMillis,
            long sizeBytes,
            boolean directory) {
        this.id = id;
        this.originalPath = originalPath;
        this.displayName = displayName;
        this.trashPath = trashPath;
        this.deletedAtMillis = deletedAtMillis;
        this.sizeBytes = sizeBytes;
        this.directory = directory;
    }
}
