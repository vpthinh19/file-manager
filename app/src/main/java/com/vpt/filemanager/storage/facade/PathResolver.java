package com.vpt.filemanager.storage.facade;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.format.ExtensionRegistry;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.handler.Handler;
import com.vpt.filemanager.handler.HandlerRegistry;
import com.vpt.filemanager.storage.virtual.Storage;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Decides which {@link Handler} opens a {@link Path}: containers use the folder handler, files are
 * classified by extension. A non-null {@code forced} type is the "open as" override. Does no I/O —
 * the name is read straight from the path.
 */
@Singleton
public final class PathResolver {
    private final ExtensionRegistry extensions;
    private final HandlerRegistry handlers;

    @Inject
    public PathResolver(ExtensionRegistry extensions, HandlerRegistry handlers) {
        this.extensions = extensions;
        this.handlers = handlers;
    }

    @NonNull
    public Handler resolve(@NonNull Path path, @NonNull Storage storage,
                           @Nullable ExtensionRegistry.Type forced) throws FileOperationException {
        ExtensionRegistry.Type type;
        if (forced != null) {
            type = forced;
        } else if (storage.isContainer(path)) {
            type = ExtensionRegistry.Type.FOLDER;
        } else {
            type = extensions.classify(nameOf(path));
        }
        return handlers.handlerFor(type);
    }

    /** The file name carried by the path, taken from the innermost archive entry when mounted. */
    private static String nameOf(Path path) {
        String raw = path.isInsideArchive() ? path.archiveInnerPath() : path.storagePath();
        int slash = raw.lastIndexOf('/');
        return slash < 0 ? raw : raw.substring(slash + 1);
    }
}
