package com.vpt.filemanager.handler;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.format.ExtensionRegistry;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.storage.virtual.Storage;

/**
 * Opens one resolved path, picked by {@code PathResolver}: folders/archives list, content files
 * render in-app or launch externally, unknown files defer to "open as".
 */
public interface Handler {
    @NonNull
    ExtensionRegistry.Type type();

    @NonNull
    OpenResult open(@NonNull Path path, @NonNull Storage storage) throws FileOperationException;
}
