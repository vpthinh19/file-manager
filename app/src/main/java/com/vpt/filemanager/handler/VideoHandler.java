package com.vpt.filemanager.handler;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.detect.ContentType;
import com.vpt.filemanager.core.path.Path;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Renders video in the in-app media player. */
@Singleton
public final class VideoHandler implements Handler {
    @Inject
    public VideoHandler() {}

    @NonNull
    @Override
    public ContentType type() {
        return ContentType.VIDEO;
    }

    @NonNull
    @Override
    public HandlerResult handle(@NonNull File materialized, @NonNull Path source) {
        return new HandlerResult.OpenContent(source, materialized.getAbsolutePath(),
                materialized.getName(), ContentType.VIDEO, true);
    }
}
