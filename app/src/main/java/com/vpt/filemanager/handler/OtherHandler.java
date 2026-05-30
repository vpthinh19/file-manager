package com.vpt.filemanager.handler;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.format.ExtensionRegistry;
import com.vpt.filemanager.core.format.MimeType;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.storage.virtual.Storage;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Fallback handler: opens a file with an external app via {@code ACTION_VIEW}. Also serves every
 * file type with no dedicated in-app handler (documents, APKs, unknown extensions).
 */
@Singleton
public final class OtherHandler implements Handler {
    @Inject
    public OtherHandler() {
    }

    @NonNull
    @Override
    public ExtensionRegistry.Type type() {
        return ExtensionRegistry.Type.EXTERNAL;
    }

    @NonNull
    @Override
    public OpenResult open(@NonNull Path path, @NonNull Storage storage)
            throws FileOperationException {
        File file = storage.materialize(path);
        return new OpenResult.LaunchIntent(path, file.getAbsolutePath(),
                MimeType.detect(file.getName()));
    }
}
