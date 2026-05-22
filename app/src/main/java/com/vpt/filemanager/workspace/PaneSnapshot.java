package com.vpt.filemanager.workspace;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Immutable read model of a pane at command dispatch time.
 */
public final class PaneSnapshot {
    @NonNull public final String paneId;
    @Nullable public final NodePath currentPath;
    @NonNull public final List<VirtualNode> visibleNodes;
    @NonNull public final Set<NodePath> selection;
    public final boolean selectionMode;

    private PaneSnapshot(@NonNull String paneId,
                         @Nullable NodePath currentPath,
                         @NonNull List<VirtualNode> visibleNodes,
                         @NonNull Set<NodePath> selection,
                         boolean selectionMode) {
        this.paneId = paneId;
        this.currentPath = currentPath;
        this.visibleNodes = List.copyOf(visibleNodes);
        this.selection = Set.copyOf(selection);
        this.selectionMode = selectionMode;
    }

    @NonNull
    public static PaneSnapshot of(@NonNull String paneId,
                                  @Nullable NodePath currentPath,
                                  @Nullable List<VirtualNode> visibleNodes,
                                  @Nullable Set<NodePath> selection,
                                  boolean selectionMode) {
        return new PaneSnapshot(
                paneId,
                currentPath,
                visibleNodes == null ? Collections.emptyList() : visibleNodes,
                selection == null ? Collections.emptySet() : selection,
                selectionMode);
    }
}
