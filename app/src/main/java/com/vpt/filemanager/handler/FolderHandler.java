package com.vpt.filemanager.handler;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.format.ExtensionRegistry;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.storage.virtual.Capabilities;
import com.vpt.filemanager.storage.virtual.Storage;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Opens a container by listing it through its storage. */
@Singleton
public final class FolderHandler implements Handler {
    @Inject
    public FolderHandler() {
    }

    @NonNull
    @Override
    public ExtensionRegistry.Type type() {
        return ExtensionRegistry.Type.FOLDER;
    }

    @NonNull
    @Override
    public OpenResult open(@NonNull Path path, @NonNull Storage storage)
            throws FileOperationException {
        return new OpenResult.Directory(path, storage.list(path), Capabilities.of(storage, path));
    }
}
