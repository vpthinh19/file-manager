package com.vpt.filemanager.core.path;

import androidx.annotation.NonNull;

import com.vpt.filemanager.content.ContentDetector;
import com.vpt.filemanager.content.ContentType;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.handler.HandlerRegistry;
import com.vpt.filemanager.handler.HandlerResult;
import com.vpt.filemanager.storage.Storage;
import com.vpt.filemanager.storage.StorageRegistry;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Opens exactly one path: ask the {@link StorageRegistry} which backend owns
 * it, list it if it is a container, otherwise materialise the file and dispatch
 * the matching {@link com.vpt.filemanager.handler.Handler}.
 *
 * <p>This used to be a ~130-line switch over schemes plus a {@code Redirect}
 * hack so the ViewModel could re-navigate when an archive file was opened.
 * That responsibility now lives entirely in {@code ArchiveStorage.handles()}
 * + {@code isContainer()} — components never see redirects.
 */
@Singleton
public final class PathResolver {
    private final StorageRegistry storages;
    private final HandlerRegistry handlers;
    private final ContentDetector detector;

    @Inject
    public PathResolver(StorageRegistry storages, HandlerRegistry handlers,
                        ContentDetector detector) {
        this.storages = storages;
        this.handlers = handlers;
        this.detector = detector;
    }

    @NonNull
    public HandlerResult open(@NonNull Path path) throws FileOperationException {
        Storage storage = storages.storageFor(path);
        if (storage.isContainer(path)) {
            return new HandlerResult.Entries(storage.list(path));
        }
        File materialized = storage.materialize(path);
        ContentType type = detector.detect(materialized);
        return handlers.handlerFor(type).handle(materialized, path);
    }
}
