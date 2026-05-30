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

/** Opens an image in the in-app viewer. */
@Singleton
public final class ImageHandler implements Handler {
    @Inject
    public ImageHandler() {
    }

    @NonNull
    @Override
    public ExtensionRegistry.Type type() {
        return ExtensionRegistry.Type.IMAGE;
    }

    @NonNull
    @Override
    public OpenResult open(@NonNull Path path, @NonNull Storage storage)
            throws FileOperationException {
        File file = storage.materialize(path);
        return new OpenResult.OpenContent(path, file.getAbsolutePath(), file.getName(),
                ContentType.IMAGE, true);
    }
}
