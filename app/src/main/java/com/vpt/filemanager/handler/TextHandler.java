package com.vpt.filemanager.handler;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.detect.ContentType;
import com.vpt.filemanager.core.path.Path;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Renders text-shaped content in the in-app editor. */
@Singleton
public final class TextHandler implements Handler {
    @Inject
    public TextHandler() {}

    @NonNull
    @Override
    public ContentType type() {
        return ContentType.TEXT;
    }

    @NonNull
    @Override
    public HandlerResult handle(@NonNull File materialized, @NonNull Path source) {
        // Read-only when the path came from an archive entry — archives still
        // need a re-import step before the in-place edit can be saved back.
        boolean readOnly = source.isInsideArchive();
        return new HandlerResult.OpenContent(source, materialized.getAbsolutePath(),
                materialized.getName(), ContentType.TEXT, readOnly);
    }
}
