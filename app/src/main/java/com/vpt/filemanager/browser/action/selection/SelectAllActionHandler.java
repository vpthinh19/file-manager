package com.vpt.filemanager.browser.action.selection;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class SelectAllActionHandler implements ActionHandler<SelectAllAction> {
    @Inject public SelectAllActionHandler() {}
    @Override public ActionResult handle(SelectAllAction action, WorkspaceSnapshot state) {
        return new ActionResult.SelectAll(action.pane());
    }
}
