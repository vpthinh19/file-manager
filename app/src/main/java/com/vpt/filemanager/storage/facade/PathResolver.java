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
 * The brain of navigation: it decides which {@link Handler} opens a pane's {@link Path}. A
 * container resolves to the folder handler; otherwise the path is classified by extension (the
 * file may live on the device, inside an archive, or inside a nested archive). Passing a non-null
 * {@code forced} type is the "open as" override that bypasses extension classification. It
 * performs no I/O: the name needed for classification is read straight from the path, so nothing
 * is extracted to cache here.
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
