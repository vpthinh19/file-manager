package com.vpt.filemanager.browser.action.browse;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.browser.item.Path;
import com.vpt.filemanager.browser.rule.StorageBoundary;
import com.vpt.filemanager.browser.workspace.state.PaneState;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class UpActionHandler implements ActionHandler<UpAction> {
    @Inject public UpActionHandler() {}
    @Override public ActionResult handle(UpAction action, WorkspaceSnapshot state)
            throws FileOperationException {
        PaneState pane = state.pane(action.pane());
        Path path = pane.path;
        if (path == null || (!path.isArchive() && !StorageBoundary.canNavigateUp(path))) {
            throw new FileOperationException("Already at root");
        }
        return new ActionResult.Navigate(action.pane(), path.parent());
    }
}
