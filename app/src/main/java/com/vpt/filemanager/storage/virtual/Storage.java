package com.vpt.filemanager.storage.virtual;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.core.entry.Entry;

import java.io.File;
import java.util.List;

/**
 * One storage backend, selected per path by {@link StorageRegistry} via {@link #handles(Path)}.
 * Only {@link #handles} and {@link #list} are mandatory; a backend stays a read-only container
 * until it overrides a mutation.
 */
public interface Storage {
    boolean handles(@NonNull Path path);

    @NonNull
    List<Entry> list(@NonNull Path path) throws FileOperationException;

    /** False only when the path is a single file; collections are always containers. */
    default boolean isContainer(@NonNull Path path) throws FileOperationException {
        return true;
    }

    default boolean canWrite(@NonNull Path path) {
        return false;
    }

    /** A real {@link File} a handler can read (local: as-is; archive: extracted to cache). */
    @NonNull
    default File materialize(@NonNull Path path) throws FileOperationException {
        throw new FileOperationException("This location is a collection, not a file");
    }

    default void create(@NonNull Path parent, @NonNull String name, boolean folder)
            throws FileOperationException {
        throw readOnly();
    }

    default void rename(@NonNull Entry entry, @NonNull String newName) throws FileOperationException {
        throw readOnly();
    }

    default void delete(@NonNull List<Entry> entries) throws FileOperationException {
        throw readOnly();
    }

    /** Copy a single entry within this same storage. */
    default void copyInternal(@NonNull Entry source, @NonNull Path destinationParent,
                              @NonNull String name, boolean replace) throws FileOperationException {
        throw readOnly();
    }

    /** Move a single entry within this same storage. */
    default void moveInternal(@NonNull Entry source, @NonNull Path destinationParent,
                              @NonNull String name, boolean replace) throws FileOperationException {
        throw readOnly();
    }

    /** Observe backing changes that may invalidate this location's rendered entries. */
    @NonNull
    default InvalidationSubscription observe(@NonNull Path path, @NonNull Runnable invalidated)
            throws FileOperationException {
        return () -> { };
    }

    private static FileOperationException readOnly() {
        return new FileOperationException("This location does not support that action");
    }
}
