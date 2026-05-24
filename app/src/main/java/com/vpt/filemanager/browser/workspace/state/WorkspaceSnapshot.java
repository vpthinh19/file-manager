package com.vpt.filemanager.browser.workspace.state;

import androidx.annotation.NonNull;

import java.util.EnumMap;

public final class WorkspaceSnapshot {
    private final EnumMap<PaneId, PaneState> panes;
    @NonNull public final PaneId activePane;

    public WorkspaceSnapshot(PaneState left, PaneState right, @NonNull PaneId activePane) {
        panes = new EnumMap<>(PaneId.class);
        panes.put(PaneId.LEFT, left);
        panes.put(PaneId.RIGHT, right);
        this.activePane = activePane;
    }

    @NonNull public PaneState pane(PaneId id) { return panes.get(id); }
    @NonNull public PaneState active() { return pane(activePane); }
    @NonNull public PaneState inactive() { return pane(activePane.other()); }
}
