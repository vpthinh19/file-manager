package com.vpt.filemanager.handler;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.format.ExtensionRegistry;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.storage.virtual.Storage;

import javax.inject.Inject;
import javax.inject.Singleton;

/** A file with no recognised type: ask the user how to open it (no work done here). */
@Singleton
public final class OpenAsHandler implements Handler {
    @Inject
    public OpenAsHandler() {
    }

    @NonNull
    @Override
    public ExtensionRegistry.Type type() {
        return ExtensionRegistry.Type.OPEN_AS;
    }

    @NonNull
    @Override
    public OpenResult open(@NonNull Path path, @NonNull Storage storage) {
        return new OpenResult.NeedsOpenAs(path);
    }
}
