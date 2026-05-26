package com.vpt.filemanager.handler;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.format.ContentType;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.path.Path;

import java.io.File;

/**
 * Turns a single materialised file plus its source {@link Path} into a
 * {@link HandlerResult}. One implementation per {@link ContentType}; the
 * {@link HandlerRegistry} picks the right one.
 *
 * <p>Folder listings do not go through a Handler — {@code Storage.list()}
 * already returns entries directly. Archive containers similarly bypass
 * handlers because {@code Storage.isContainer()} routes them.
 */
public interface Handler {
    @NonNull
    ContentType type();

    @NonNull
    HandlerResult handle(@NonNull File materialized, @NonNull Path source)
            throws FileOperationException;
}
