package com.vpt.filemanager.operations.share;

import androidx.annotation.NonNull;

import java.util.List;

import com.vpt.filemanager.node.NodePath;

/**
 * UI-neutral share request. Android boundary converts local paths to FileProvider URIs.
 */
public final class ShareRequest {
    @NonNull public final List<NodePath> localPaths;

    public ShareRequest(@NonNull List<NodePath> localPaths) {
        this.localPaths = List.copyOf(localPaths);
    }

    public boolean isEmpty() {
        return localPaths.isEmpty();
    }

    public boolean multiple() {
        return localPaths.size() > 1;
    }
}
