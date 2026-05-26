package com.vpt.filemanager.storage.facade;

import androidx.annotation.NonNull;

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
 * file may live on the device, inside an archive, or inside a nested archive) — or by an explicit
 * {@code OpenMode} chosen from "open as". It performs no I/O: the name needed for classification
 * is read straight from the path, so nothing is extracted to cache here.
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
    public Handler resolve(@NonNull Path path, @NonNull Storage storage, @NonNull OpenMode mode)
            throws FileOperationException {
        ExtensionRegistry.Type type;
        if (mode != OpenMode.DEFAULT) {
            type = explicit(mode);
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

    private static ExtensionRegistry.Type explicit(OpenMode mode) {
        return switch (mode) {
            case TEXT -> ExtensionRegistry.Type.TEXT;
            case IMAGE -> ExtensionRegistry.Type.IMAGE;
            case AUDIO -> ExtensionRegistry.Type.AUDIO;
            case VIDEO -> ExtensionRegistry.Type.VIDEO;
            case ARCHIVE -> ExtensionRegistry.Type.ARCHIVE;
            case DEFAULT -> throw new IllegalStateException("DEFAULT mode is implicit");
        };
    }
}
