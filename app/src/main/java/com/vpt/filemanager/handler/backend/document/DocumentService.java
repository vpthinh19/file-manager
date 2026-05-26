package com.vpt.filemanager.handler.backend.document;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.handler.backend.archive.ArchiveBackend;
import com.vpt.filemanager.storage.LocalStorageAdapter;

@Singleton
public final class DocumentService {
    private final LocalStorageAdapter files;
    private final ArchiveBackend archives;

    @Inject
    public DocumentService(LocalStorageAdapter files, ArchiveBackend archives) {
        this.files = files;
        this.archives = archives;
    }

    public DocumentSession open(@NonNull String localPath, @Nullable Path archiveEntry) {
        return new DocumentSession(localPath, archiveEntry, files, archives);
    }
}
