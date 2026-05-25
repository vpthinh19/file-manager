package com.vpt.filemanager.storage.local;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.entry.Entry;
import com.vpt.filemanager.storage.LocalStorageAdapter;
import com.vpt.filemanager.storage.Storage;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@link Storage} backed by the device's external storage. Wraps the existing
 * {@link LocalStorageAdapter} until later phases collapse the two.
 */
@Singleton
public final class LocalStorage implements Storage {
    private final LocalStorageAdapter files;

    @Inject
    public LocalStorage(LocalStorageAdapter files) {
        this.files = files;
    }

    @Override
    public boolean handles(@NonNull Path path) {
        return path.isStorage() && !path.isInsideArchive();
    }

    @Override
    public boolean isContainer(@NonNull Path path) throws FileOperationException {
        if (path.isStorageRoot()) return true;
        File file = files.resolve(path);
        return file.isDirectory();
    }

    @NonNull
    @Override
    public List<Entry> list(@NonNull Path path) throws FileOperationException {
        File directory = files.resolve(path);
        if (!directory.isDirectory()) {
            throw new FileOperationException("Not a directory: " + path);
        }
        List<Entry> entries = new ArrayList<>();
        Path parent = path.parent();
        if (parent != null) entries.add(Entry.parent(parent));
        for (File child : files.children(directory)) {
            entries.add(Entry.local(files.pathOf(child), child.getAbsolutePath(), child.getName(),
                    child.isDirectory(), child.isDirectory() ? -1L : child.length(),
                    child.lastModified()));
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
        files.rename(new File(entry.localPath()), newName);
    }

    @Override
    public void delete(@NonNull List<Entry> entries) throws FileOperationException {
        for (Entry entry : entries) {
            if (entry.isParent()) continue;
            files.deletePermanently(new File(entry.localPath()));
        }
    }

    @Override
    public void copyInternal(@NonNull Entry source, @NonNull Path destinationParent,
                             @NonNull String name) throws FileOperationException {
        File destination = new File(files.fileAtStoragePath(destinationParent.storagePath()), name);
        files.copy(new File(source.localPath()), destination);
    }

    @Override
    public void moveInternal(@NonNull Entry source, @NonNull Path destinationParent,
                             @NonNull String name) throws FileOperationException {
        File destination = new File(files.fileAtStoragePath(destinationParent.storagePath()), name);
        files.move(new File(source.localPath()), destination);
    }

    @NonNull
    @Override
    public InputStream openRead(@NonNull Entry entry) throws FileOperationException {
        return files.openRead(new File(entry.localPath()));
    }

    @NonNull
    @Override
    public OutputStream openWrite(@NonNull Entry entry) throws FileOperationException {
        return files.openWrite(new File(entry.localPath()));
    }
}
