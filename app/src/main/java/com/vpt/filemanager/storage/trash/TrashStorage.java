package com.vpt.filemanager.storage.trash;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.storage.LocalStorageAdapter;
import com.vpt.filemanager.storage.Storage;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/** {@link Storage} for the virtual Trash collection. Wraps {@link TrashCollection}. */
@Singleton
public final class TrashStorage implements Storage {
    private final TrashCollection trash;
    private final LocalStorageAdapter files;

    @Inject
    public TrashStorage(TrashCollection trash, LocalStorageAdapter files) {
        this.trash = trash;
        this.files = files;
    }

    @Override
    public boolean handles(@NonNull Path path) {
        return path.isTrash();
    }

    @Override
    public boolean isContainer(@NonNull Path path) {
        return true;
    }

    @NonNull
    @Override
    public List<Entry> list(@NonNull Path path) {
        return trash.list();
    }

    @NonNull
    @Override
    public File materialize(@NonNull Path path) throws FileOperationException {
        throw new FileOperationException("Trash is a collection, not a file");
    }

    @Override
    public boolean canWrite(@NonNull Path path) {
        return false;
    }

    @Override
    public void create(@NonNull Path parent, @NonNull String name, boolean folder)
            throws FileOperationException {
        throw new FileOperationException("Cannot create entries inside trash");
    }

    @Override
    public void rename(@NonNull Entry entry, @NonNull String newName) throws FileOperationException {
        throw new FileOperationException("Cannot rename trashed entries");
    }

    @Override
    public void delete(@NonNull List<Entry> entries) throws FileOperationException {
        for (Entry entry : entries) {
            if (entry.isTrashItem()) trash.deletePermanently(entry);
        }
    }

    @Override
    public void copyInternal(@NonNull Entry source, @NonNull Path destinationParent,
                             @NonNull String name, boolean replace) throws FileOperationException {
        throw new FileOperationException("Cannot copy within trash");
    }

    @Override
    public void moveInternal(@NonNull Entry source, @NonNull Path destinationParent,
                             @NonNull String name, boolean replace) throws FileOperationException {
        throw new FileOperationException("Cannot move within trash");
    }

    @NonNull
    @Override
    public InputStream openRead(@NonNull Entry entry) throws FileOperationException {
        return files.openRead(files.fromAbsolutePath(entry.localPath()));
    }

    @NonNull
    @Override
    public OutputStream openWrite(@NonNull Entry entry) throws FileOperationException {
        throw new FileOperationException("Trash entries are read-only");
    }
}
