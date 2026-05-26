package com.vpt.filemanager.handler;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.format.ContentType;
import com.vpt.filemanager.core.path.Path;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Renders an image in the in-app viewer. */
@Singleton
public final class ImageHandler implements Handler {
    @Inject
    public ImageHandler() {}

    @NonNull
    @Override
    public ContentType type() {
        return ContentType.IMAGE;
    }

    @NonNull
    @Override
    public HandlerResult handle(@NonNull File materialized, @NonNull Path source) {
        return new HandlerResult.OpenContent(source, materialized.getAbsolutePath(),
                materialized.getName(), ContentType.IMAGE, true);
    }
}
