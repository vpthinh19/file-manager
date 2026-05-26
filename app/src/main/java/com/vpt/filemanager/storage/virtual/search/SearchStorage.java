package com.vpt.filemanager.storage.virtual.search;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.storage.physical.local.LocalStorageAdapter;
import com.vpt.filemanager.storage.virtual.Storage;
import com.vpt.filemanager.storage.virtual.InvalidationSubscription;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@link Storage} that scans physical device files for a name match. A search path is
 * read-only and always behaves as a flat container.
 */
@Singleton
public final class SearchStorage implements Storage {
    private static final int MAX_RESULTS = 1000;
    private static final int MAX_DIRECTORIES = 10000;
    private final LocalStorageAdapter files;

    @Inject
    public SearchStorage(LocalStorageAdapter files) {
        this.files = files;
    }

    @Override
    public boolean handles(@NonNull Path path) {
        return path.isSearch();
    }

    @Override
    public boolean isContainer(@NonNull Path path) {
        return true;
    }

    @NonNull
    @Override
    public List<Entry> list(@NonNull Path path) throws FileOperationException {
        String query = path.query().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return List.of();
        ArrayDeque<File> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        List<Entry> found = new ArrayList<>();
        pending.add(files.fileAtStoragePath(path.storagePath()));
        while (!pending.isEmpty() && visited.size() < MAX_DIRECTORIES && found.size() < MAX_RESULTS) {
            if (Thread.currentThread().isInterrupted()) {
                throw new FileOperationException("Search cancelled");
            }
            File directory = pending.removeFirst();
            if (!visited.add(files.absolutePath(directory))) continue;
            List<File> children;
            try {
                children = files.children(directory);
            } catch (FileOperationException denied) {
                if (visited.size() == 1) throw denied;
                continue;
            }
            for (File child : children) {
                boolean folder = files.isDirectory(child);
                if (files.name(child).toLowerCase(Locale.ROOT).contains(query)) {
                    found.add(Entry.local(files.pathOf(child), files.absolutePath(child),
                            files.name(child), folder, folder ? -1L : files.size(child),
                            files.modifiedAt(child)));
                }
                if (folder) pending.addLast(child);
                if (found.size() >= MAX_RESULTS) break;
            }
        }
        return found;
    }

    @NonNull
    @Override
    public File materialize(@NonNull Path path) throws FileOperationException {
        throw new FileOperationException("Search results are listings, not files");
    }

    @Override
    public boolean canWrite(@NonNull Path path) {
        return false;
    }

    @Override
    public void create(@NonNull Path parent, @NonNull String name, boolean folder)
            throws FileOperationException {
        throw new FileOperationException("Cannot create entries inside a search");
    }

    @Override
    public void rename(@NonNull Entry entry, @NonNull String newName) throws FileOperationException {
        throw new FileOperationException("Cannot rename search results");
    }

    @Override
    public void delete(@NonNull List<Entry> entries) throws FileOperationException {
        throw new FileOperationException("Cannot delete from a search; open the actual location instead");
    }

    @Override
    public void copyInternal(@NonNull Entry source, @NonNull Path destinationParent,
                             @NonNull String name, boolean replace) throws FileOperationException {
        throw new FileOperationException("Cannot copy within a search");
    }

    @Override
    public void moveInternal(@NonNull Entry source, @NonNull Path destinationParent,
                             @NonNull String name, boolean replace) throws FileOperationException {
        throw new FileOperationException("Cannot move within a search");
    }

    @NonNull
    @Override
    public InputStream openRead(@NonNull Entry entry) throws FileOperationException {
        return files.openRead(files.fromAbsolutePath(entry.localPath()));
    }

    @NonNull
    @Override
    public OutputStream openWrite(@NonNull Entry entry) throws FileOperationException {
        throw new FileOperationException("Search entries are read-only");
    }

    @NonNull
    @Override
    public InvalidationSubscription observe(@NonNull Path path, @NonNull Runnable invalidated)
            throws FileOperationException {
        return files.observeDirectory(files.fileAtStoragePath(path.storagePath()), invalidated);
    }
}
