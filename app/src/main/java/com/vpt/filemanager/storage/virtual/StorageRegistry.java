package com.vpt.filemanager.storage.virtual;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.path.Path;

import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Maps a {@link Path} to the {@link Storage} backend that owns it.
 *
 * <p>Hilt collects every {@code @IntoSet Storage} binding into the constructor's
 * {@code Set<Storage>}. Adding a new backend means adding one {@code @Provides @IntoSet}
 * entry in {@code di.StorageModule}; nothing in this class changes.
 */
@Singleton
public final class StorageRegistry {
    private final Set<Storage> storages;

    @Inject
    public StorageRegistry(Set<Storage> storages) {
        this.storages = storages;
    }

    @NonNull
    public Storage storageFor(@NonNull Path path) throws FileOperationException {
        for (Storage storage : storages) {
            if (storage.handles(path)) return storage;
        }
        throw new FileOperationException("No storage handles path: " + path);
    }
}
