package com.vpt.filemanager.handler;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.format.ContentType;
import com.vpt.filemanager.core.path.Path;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Renders extension-routed text content in the in-app editor. */
@Singleton
public final class TextHandler implements Handler {
    @Inject
    public TextHandler() {
    }

    @NonNull
    @Override
    public ContentType type() {
        return ContentType.TEXT;
    }

    @NonNull
    @Override
    public HandlerResult handle(@NonNull File materialized, @NonNull Path source) {
        // The facade applies storage capabilities after this handler chooses the editor.
        return new HandlerResult.OpenContent(source, materialized.getAbsolutePath(),
                materialized.getName(), ContentType.TEXT, false);
    }
}
