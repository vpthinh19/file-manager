package com.vpt.filemanager.storage;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.core.entry.Entry;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * One storage backend. The {@link StorageRegistry} consults each implementation's
 * {@link #handles(Path)} to route a path to the right backend.
 *
 * <p>Implementations cover local files, mounted archives, virtual collections
 * (trash, bookmarks, search), and any future remote source. Cross-backend
 * transfers are orchestrated by {@code StorageFacade} using
 * {@link #materialize(Path)} on the source plus a mutation on the destination.
 */
public interface Storage {
    boolean handles(@NonNull Path path);

    /**
     * True when this path lists children (folder, archive folder or mount root,
     * trash, bookmarks, or a search result). False when it points at a single file
     * that should be opened by a handler.
     */
    boolean isContainer(@NonNull Path path) throws FileOperationException;

    @NonNull
    List<Entry> list(@NonNull Path path) throws FileOperationException;

    /**
     * Produce a real {@link File} a handler can read. Local returns the file as
     * is; archive extracts to cache; a future remote backend would download to
     * cache.
     */
    @NonNull
    File materialize(@NonNull Path path) throws FileOperationException;

    boolean canWrite(@NonNull Path path);

    void create(@NonNull Path parent, @NonNull String name, boolean folder)
            throws FileOperationException;

    void rename(@NonNull Entry entry, @NonNull String newName) throws FileOperationException;

    void delete(@NonNull List<Entry> entries) throws FileOperationException;

    /** Copy a single entry within this same storage. */
    void copyInternal(@NonNull Entry source, @NonNull Path destinationParent, @NonNull String name,
                      boolean replace)
            throws FileOperationException;

    /** Move a single entry within this same storage. */
    void moveInternal(@NonNull Entry source, @NonNull Path destinationParent, @NonNull String name,
                      boolean replace)
            throws FileOperationException;

    @NonNull
    InputStream openRead(@NonNull Entry entry) throws FileOperationException;

    @NonNull
    OutputStream openWrite(@NonNull Entry entry) throws FileOperationException;

    /** Observe backing changes that may invalidate this location's rendered entries. */
    @NonNull
    default InvalidationSubscription observe(@NonNull Path path, @NonNull Runnable invalidated)
            throws FileOperationException {
        return () -> { };
    }
}
