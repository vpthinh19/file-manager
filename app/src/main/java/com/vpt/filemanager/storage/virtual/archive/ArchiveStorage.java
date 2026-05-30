package com.vpt.filemanager.storage.virtual.archive;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.storage.physical.local.LocalStorageAdapter;
import com.vpt.filemanager.storage.virtual.InvalidationSubscription;
import com.vpt.filemanager.storage.virtual.Storage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Virtual storage for paths already mounted inside an archive container. */
@Singleton
public final class ArchiveStorage implements Storage {
    private final ArchiveBackend archives;
    private final LocalStorageAdapter files;

    @Inject
    public ArchiveStorage(ArchiveBackend archives, LocalStorageAdapter files) {
        this.archives = archives;
        this.files = files;
    }

    @Override
    public boolean handles(@NonNull Path path) {
        return path.isInsideArchive();
    }

    @Override
    public boolean isContainer(@NonNull Path path) throws FileOperationException {
        if (!path.isInsideArchive()) return false;
        return archives.isDirectory(path);
    }

    @NonNull
    @Override
    public List<Entry> list(@NonNull Path path) throws FileOperationException {
        List<Entry> entries = new ArrayList<>();
        Path parent = path.parent();
        if (parent != null) entries.add(Entry.parent(parent));
        entries.addAll(archives.list(path));
        return entries;
    }

    @NonNull
    @Override
    public File materialize(@NonNull Path path) throws FileOperationException {
        if (!path.isInsideArchive()) {
            throw new FileOperationException("Archive containers are listed, not materialized");
        }
        String inner = path.archiveInnerPath();
        int slash = inner.lastIndexOf('/');
        String name = slash < 0 ? inner : inner.substring(slash + 1);
        return files.fromAbsolutePath(archives.materialize(Entry.archive(path, name, false, 0L, 0L)));
    }

    @Override
    public boolean canWrite(@NonNull Path path) {
        return path.isInsideArchive() && archives.canWrite(path);
    }

    @Override
    public void create(@NonNull Path parent, @NonNull String name, boolean folder)
            throws FileOperationException {
        archives.create(parent, name, folder);
    }

    @Override
    public void rename(@NonNull Entry entry, @NonNull String newName) throws FileOperationException {
        archives.rename(entry, newName);
    }

    @Override
    public void delete(@NonNull List<Entry> entries) throws FileOperationException {
        archives.delete(entries);
    }

    @Override
    public void copyInternal(@NonNull Entry source, @NonNull Path destinationParent,
                             @NonNull String name, boolean replace) throws FileOperationException {
        archives.importFromArchive(destinationParent, source, name, replace);
    }

    @Override
    public void moveInternal(@NonNull Entry source, @NonNull Path destinationParent,
                             @NonNull String name, boolean replace) throws FileOperationException {
        archives.importFromArchive(destinationParent, source, name, replace);
        archives.delete(List.of(source));
    }

    public void importEntry(@NonNull Path destinationParent, @NonNull Entry source,
                            @NonNull String name, boolean replace) throws FileOperationException {
        if (source.isInsideArchive()) {
            archives.importFromArchive(destinationParent, source, name, replace);
        } else {
            archives.importFromStorage(destinationParent, source, name, replace);
        }
    }

    public void extractToDevice(@NonNull Entry source, @NonNull Path destinationParent,
                                @NonNull String name, boolean replace) throws FileOperationException {
        archives.extractToStorage(source, destinationParent, name, replace);
    }

    @NonNull
    @Override
    public InvalidationSubscription observe(@NonNull Path path, @NonNull Runnable invalidated)
            throws FileOperationException {
        File container = files.fileAtStoragePath(path.storagePath());
        File parent = container.getParentFile();
        return parent == null ? () -> { } : files.observeDirectory(parent, invalidated);
    }
}
