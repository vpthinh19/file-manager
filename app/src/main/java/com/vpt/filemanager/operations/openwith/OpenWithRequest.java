package com.vpt.filemanager.operations.openwith;

import androidx.annotation.NonNull;

import com.vpt.filemanager.node.NodePath;

/**
 * UI-neutral open-with request. Android boundary converts the path to a FileProvider URI.
 */
public final class OpenWithRequest {
    @NonNull public final NodePath localPath;
    @NonNull public final String mimeType;

    public OpenWithRequest(@NonNull NodePath localPath, @NonNull String mimeType) {
        this.localPath = localPath;
        this.mimeType = mimeType;
    }
}
