package com.vpt.filemanager.storage.virtual.trash;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.storage.virtual.Storage;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Read-only {@link Storage} for the virtual Trash collection; deleting here is permanent. */
@Singleton
public final class TrashStorage implements Storage {
    private final TrashCollection trash;

    @Inject
    public TrashStorage(TrashCollection trash) {
        this.trash = trash;
    }

    @Override
    public boolean handles(@NonNull Path path) {
        return path.isTrash();
    }

    @NonNull
    @Override
    public List<Entry> list(@NonNull Path path) {
        return trash.list();
    }

    @Override
    public void delete(@NonNull List<Entry> entries) throws FileOperationException {
        for (Entry entry : entries) {
            if (entry.isTrashItem()) trash.deletePermanently(entry);
        }
    }
}
