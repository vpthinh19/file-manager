package com.vpt.filemanager.handler;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.format.ExtensionRegistry;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.storage.virtual.Storage;

/**
 * Opens one resolved path. Every path type has a handler: folders and archives list, content
 * files render in-app or launch externally, and an unknown file defers to "open as". The
 * {@link com.vpt.filemanager.storage.facade.PathResolver} picks the handler; the handler does the
 * work itself or calls a backend (e.g. the editor's document service, the archive engine).
 */
public interface Handler {
    @NonNull
    ExtensionRegistry.Type type();

    @NonNull
    OpenResult open(@NonNull Path path, @NonNull Storage storage) throws FileOperationException;
}
