package com.vpt.filemanager.browser.action.selection;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class ClearSelectionActionHandler implements ActionHandler<ClearSelectionAction> {
    @Inject public ClearSelectionActionHandler() {}
    @Override public ActionResult handle(ClearSelectionAction action, WorkspaceSnapshot state) {
        return new ActionResult.ClearSelection(action.pane(), action.exitMode());
    }
}
