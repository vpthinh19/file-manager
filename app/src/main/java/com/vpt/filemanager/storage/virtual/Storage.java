package com.vpt.filemanager.storage.virtual;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.core.entry.Entry;

import java.io.File;
import java.util.List;

/**
 * One storage backend. {@link StorageRegistry} routes a path to the right backend by asking each
 * implementation's {@link #handles(Path)}.
 *
 * <p>Only {@link #handles} and {@link #list} are mandatory. Everything else has a default:
 * a backend is a read-only container unless it overrides the relevant capability. Read-only
 * collections (trash, bookmarks, search) therefore declare just what they support; writable
 * backends (device, archive) override the mutations. Cross-backend transfers are orchestrated by
 * {@code StorageFacade} using {@link #materialize(Path)} on the source plus a mutation on the
 * destination.
 */
public interface Storage {
    boolean handles(@NonNull Path path);

    @NonNull
    List<Entry> list(@NonNull Path path) throws FileOperationException;

    /**
     * True when this path lists children (folder, archive folder or mount root, trash, bookmarks,
     * or a search result). False when it points at a single file opened by a handler. Collections
     * are always containers; only file-bearing backends override this.
     */
    default boolean isContainer(@NonNull Path path) throws FileOperationException {
        return true;
    }

    default boolean canWrite(@NonNull Path path) {
        return false;
    }

    /**
     * Produce a real {@link File} a handler can read. Local returns the file as is; archive
     * extracts to cache; a future remote backend would download to cache.
     */
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
