package com.vpt.filemanager.storage.archive;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.storage.LocalStorageAdapter;
import com.vpt.filemanager.storage.InvalidationSubscription;
import com.vpt.filemanager.storage.Storage;
import com.vpt.filemanager.handler.backend.archive.ArchiveBackend;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@link Storage} for archive containers. Claims paths that either point inside
 * a mounted archive ({@code storage:/x.zip!/...}) or to a file whose name has a
 * known archive extension. The container is opened transparently — there is no
 * "mount as folder" step the UI has to drive.
 */
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
        if (!path.isInsideArchive()) return true; // bare archive file => act as root
        return archives.isDirectory(path);
    }

    @NonNull
    @Override
    public List<Entry> list(@NonNull Path path) throws FileOperationException {
        Path target = path.isInsideArchive() ? path : Path.archive(path.storagePath(), "/");
        List<Entry> entries = new ArrayList<>();
        Path parent = target.parent();
        if (parent != null) entries.add(Entry.parent(parent));
        entries.addAll(archives.list(target));
        return entries;
    }

    @NonNull
    @Override
    public File materialize(@NonNull Path path) throws FileOperationException {
        if (!path.isInsideArchive()) {
            // A bare archive file is always a container; materialize is only ever
            // called by the resolver on a non-container path.
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
    public InputStream openRead(@NonNull Entry entry) throws FileOperationException {
        return files.openRead(files.fromAbsolutePath(archives.materialize(entry)));
    }

    @NonNull
    @Override
    public OutputStream openWrite(@NonNull Entry entry) throws FileOperationException {
        throw new FileOperationException("Archive entries are not directly writable; use materialize + update");
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
