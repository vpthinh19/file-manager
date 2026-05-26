package com.vpt.filemanager.component.content.editor;

import androidx.annotation.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.storage.LocalStorageAdapter;

@Singleton
public final class DocumentService {
    private final LocalStorageAdapter files;

    @Inject
    public DocumentService(LocalStorageAdapter files) {
        this.files = files;
    }

    public DocumentSession open(@NonNull String localPath) {
        return new DocumentSession(localPath, files);
    }
}
