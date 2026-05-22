package com.vpt.filemanager.workspace;

import androidx.annotation.NonNull;

/**
 * Immutable read model of the dual-pane workspace at command dispatch time.
 */
public final class WorkspaceSnapshot {
    @NonNull public final PaneSnapshot activePane;
    @NonNull public final PaneSnapshot inactivePane;

    public WorkspaceSnapshot(@NonNull PaneSnapshot activePane,
                             @NonNull PaneSnapshot inactivePane) {
        this.activePane = activePane;
        this.inactivePane = inactivePane;
    }
}
