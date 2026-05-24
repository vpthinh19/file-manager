package com.vpt.filemanager.browser.action.selection;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class ToggleSelectionActionHandler implements ActionHandler<ToggleSelectionAction> {
    @Inject public ToggleSelectionActionHandler() {}
    @Override public ActionResult handle(ToggleSelectionAction action, WorkspaceSnapshot state) {
        return new ActionResult.ToggleSelection(action.pane(), action.item(), action.enterMode());
    }
}
