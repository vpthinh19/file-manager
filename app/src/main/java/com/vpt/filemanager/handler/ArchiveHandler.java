package com.vpt.filemanager.handler;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.format.ExtensionRegistry;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.storage.virtual.Capabilities;
import com.vpt.filemanager.storage.virtual.Storage;
import com.vpt.filemanager.storage.virtual.StorageRegistry;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Opens an archive by mounting it as a directory root; the mounted path routes to
 * {@code ArchiveStorage} and keeps its parent boundaries, so nested archives work.
 */
@Singleton
public final class ArchiveHandler implements Handler {
    private final StorageRegistry storages;

    @Inject
    public ArchiveHandler(StorageRegistry storages) {
        this.storages = storages;
    }

    @NonNull
    @Override
    public ExtensionRegistry.Type type() {
        return ExtensionRegistry.Type.ARCHIVE;
    }

    @NonNull
    @Override
    public OpenResult open(@NonNull Path path, @NonNull Storage storage)
            throws FileOperationException {
        Path mounted = path.mountArchive();
        Storage archive = storages.storageFor(mounted);
        if (!archive.isContainer(mounted)) throw new FileOperationException("Invalid archive");
        return new OpenResult.Directory(mounted, archive.list(mounted),
                Capabilities.of(archive, mounted));
    }
}
