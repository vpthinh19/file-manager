package com.vpt.filemanager.browser.action;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

/** Business implementation for one action request. It does not touch Android views. */
public interface ActionHandler<A extends Action> {
    @NonNull
    ActionResult handle(@NonNull A action, @NonNull WorkspaceSnapshot state)
            throws FileOperationException;
}
