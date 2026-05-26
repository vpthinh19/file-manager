package com.vpt.filemanager.storage.device;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.storage.InvalidationSubscription;
import com.vpt.filemanager.storage.LocalStorageAdapter;
import com.vpt.filemanager.storage.Storage;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Virtual device-storage partition backed exclusively by {@link LocalStorageAdapter}. */
@Singleton
public final class DeviceStorage implements Storage {
    private final LocalStorageAdapter files;

    @Inject
    public DeviceStorage(LocalStorageAdapter files) {
        this.files = files;
    }

    @Override
    public boolean handles(@NonNull Path path) {
        return path.isStorage() && !path.isInsideArchive();
    }

    @Override
    public boolean isContainer(@NonNull Path path) throws FileOperationException {
        if (path.isStorageRoot()) return true;
        return files.isDirectory(files.resolve(path));
    }

    @NonNull
    @Override
    public List<Entry> list(@NonNull Path path) throws FileOperationException {
        File directory = files.resolve(path);
        if (!files.isDirectory(directory)) throw new FileOperationException("Not a directory: " + path);
        List<Entry> entries = new ArrayList<>();
        Path parent = path.parent();
        if (parent != null) entries.add(Entry.parent(parent));
        for (File child : files.children(directory)) {
            boolean folder = files.isDirectory(child);
            entries.add(Entry.local(files.pathOf(child), files.absolutePath(child), files.name(child),
                    folder, folder ? -1L : files.size(child), files.modifiedAt(child)));
        }
        return entries;
    }

    @NonNull
    @Override
    public File materialize(@NonNull Path path) throws FileOperationException {
        return files.resolve(path);
    }

    @Override
    public boolean canWrite(@NonNull Path path) {
        return handles(path);
    }

    @Override
    public void create(@NonNull Path parent, @NonNull String name, boolean folder)
            throws FileOperationException {
        files.create(files.fileAtStoragePath(parent.storagePath()), name, folder);
    }

    @Override
    public void rename(@NonNull Entry entry, @NonNull String newName) throws FileOperationException {
        files.rename(files.fromAbsolutePath(entry.localPath()), newName);
    }

    @Override
    public void delete(@NonNull List<Entry> entries) throws FileOperationException {
        for (Entry entry : entries) {
            if (!entry.isParent()) files.deletePermanently(files.fromAbsolutePath(entry.localPath()));
        }
    }

    @Override
    public void copyInternal(@NonNull Entry source, @NonNull Path destinationParent,
                             @NonNull String name, boolean replace) throws FileOperationException {
        File destination = files.target(files.fileAtStoragePath(destinationParent.storagePath()), name);
        if (replace) files.copyReplacing(files.fromAbsolutePath(source.localPath()), destination);
        else files.copy(files.fromAbsolutePath(source.localPath()), destination);
    }

    @Override
    public void moveInternal(@NonNull Entry source, @NonNull Path destinationParent,
                             @NonNull String name, boolean replace) throws FileOperationException {
        File destination = files.target(files.fileAtStoragePath(destinationParent.storagePath()), name);
        if (replace) files.moveReplacing(files.fromAbsolutePath(source.localPath()), destination);
        else files.move(files.fromAbsolutePath(source.localPath()), destination);
    }

    @NonNull
    @Override
    public InputStream openRead(@NonNull Entry entry) throws FileOperationException {
        return files.openRead(files.fromAbsolutePath(entry.localPath()));
    }

    @NonNull
    @Override
    public OutputStream openWrite(@NonNull Entry entry) throws FileOperationException {
        return files.openWrite(files.fromAbsolutePath(entry.localPath()));
    }

    @NonNull
    @Override
    public InvalidationSubscription observe(@NonNull Path path, @NonNull Runnable invalidated)
            throws FileOperationException {
        File target = files.resolve(path);
        return files.observeDirectory(files.isDirectory(target) ? target : files.parent(target), invalidated);
    }
}
