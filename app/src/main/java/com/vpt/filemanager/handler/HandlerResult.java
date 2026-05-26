package com.vpt.filemanager.handler;

import com.vpt.filemanager.core.format.ContentType;
import com.vpt.filemanager.core.path.Path;

/** Outcome of opening a non-container path through a handler. */
public sealed interface HandlerResult
        permits HandlerResult.OpenContent, HandlerResult.LaunchIntent {

    record OpenContent(Path source, String localPath, String displayName,
                       ContentType type, boolean readOnly) implements HandlerResult {
    }

    record LaunchIntent(Path source, String localPath, String mimeType) implements HandlerResult {
    }
}
