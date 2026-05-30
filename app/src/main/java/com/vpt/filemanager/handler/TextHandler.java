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

/** Opens extension-routed text in the in-app editor; read-only inside non-writable archives. */
@Singleton
public final class TextHandler implements Handler {
    @Inject
    public TextHandler() {
    }

    @NonNull
    @Override
    public ExtensionRegistry.Type type() {
        return ExtensionRegistry.Type.TEXT;
    }

    @NonNull
    @Override
    public OpenResult open(@NonNull Path path, @NonNull Storage storage)
            throws FileOperationException {
        File file = storage.materialize(path);
        boolean readOnly = path.isInsideArchive() && !storage.canWrite(path);
        return new OpenResult.OpenContent(path, file.getAbsolutePath(), file.getName(),
                ContentType.TEXT, readOnly);
    }
}
