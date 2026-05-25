package com.vpt.filemanager.handler;

import androidx.annotation.NonNull;

import com.vpt.filemanager.content.ContentType;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.ui.format.MimeTypes;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Fallback for anything the detector marks {@link ContentType#EXTERNAL}. Returns
 * a {@link HandlerResult.LaunchIntent} so the component can fire
 * {@code Intent.ACTION_VIEW} for the user's chosen external app.
 */
@Singleton
public final class OtherHandler implements Handler {
    @Inject
    public OtherHandler() {}

    @NonNull
    @Override
    public ContentType type() {
        return ContentType.EXTERNAL;
    }

    @NonNull
    @Override
    public HandlerResult handle(@NonNull File materialized, @NonNull Path source) {
        return new HandlerResult.LaunchIntent(source, materialized.getAbsolutePath(),
                MimeTypes.detect(materialized.getName()));
    }
}
