package com.vpt.filemanager.operations.transfer;

import androidx.annotation.NonNull;

import com.vpt.filemanager.node.VirtualNode;

/**
 * Conflict context for a target sibling that already uses the requested name.
 */
public final class NameConflict {
    @NonNull public final VirtualNode targetParent;
    @NonNull public final VirtualNode existing;
    @NonNull public final String requestedName;

    public NameConflict(@NonNull VirtualNode targetParent,
                        @NonNull VirtualNode existing,
                        @NonNull String requestedName) {
        this.targetParent = targetParent;
        this.existing = existing;
        this.requestedName = requestedName;
    }
}
