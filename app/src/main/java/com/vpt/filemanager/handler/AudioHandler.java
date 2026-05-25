package com.vpt.filemanager.handler;

import androidx.annotation.NonNull;

import com.vpt.filemanager.content.ContentType;
import com.vpt.filemanager.core.path.Path;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Renders audio in the in-app media player. */
@Singleton
public final class AudioHandler implements Handler {
    @Inject
    public AudioHandler() {}

    @NonNull
    @Override
    public ContentType type() {
        return ContentType.AUDIO;
    }

    @NonNull
    @Override
    public HandlerResult handle(@NonNull File materialized, @NonNull Path source) {
        return new HandlerResult.OpenContent(source, materialized.getAbsolutePath(),
                materialized.getName(), ContentType.AUDIO, true);
    }
}
