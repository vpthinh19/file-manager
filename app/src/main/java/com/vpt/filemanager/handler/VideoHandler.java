package com.vpt.filemanager.handler;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.format.ContentType;
import com.vpt.filemanager.core.format.ExtensionRegistry;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.storage.virtual.Storage;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Opens video in the in-app media player. */
@Singleton
public final class VideoHandler implements Handler {
    @Inject
    public VideoHandler() {
    }

    @NonNull
    @Override
    public ExtensionRegistry.Type type() {
        return ExtensionRegistry.Type.VIDEO;
    }

    @NonNull
    @Override
    public OpenResult open(@NonNull Path path, @NonNull Storage storage)
            throws FileOperationException {
        File file = storage.materialize(path);
        return new OpenResult.OpenContent(path, file.getAbsolutePath(), file.getName(),
                ContentType.VIDEO, true);
    }
}
