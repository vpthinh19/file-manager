package com.vpt.filemanager.browser.ui.pane;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.vpt.filemanager.browser.action.Action;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public interface PaneController {
    @NonNull LiveData<WorkspaceSnapshot> workspaceState();
    void dispatch(@NonNull Action action);
}
