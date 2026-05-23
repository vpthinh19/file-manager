package com.vpt.filemanager.workspace;

import androidx.annotation.NonNull;

import java.util.List;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Immutable materialized children of one virtual directory at a workspace revision.
 */
public final class DirectorySnapshot {
    @NonNull public final NodePath containerPath;
    public final long revision;
    @NonNull public final List<VirtualNode> children;

    public DirectorySnapshot(@NonNull NodePath containerPath,
                             long revision,
                             @NonNull List<VirtualNode> children) {
        this.containerPath = containerPath;
        this.revision = revision;
        this.children = List.copyOf(children);
    }
}
