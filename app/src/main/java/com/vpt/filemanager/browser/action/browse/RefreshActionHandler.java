package com.vpt.filemanager.browser.action.browse;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class RefreshActionHandler implements ActionHandler<RefreshAction> {
    @Inject public RefreshActionHandler() {}
    @Override public ActionResult handle(RefreshAction action, WorkspaceSnapshot state) {
        return new ActionResult.Refresh(action.pane());
    }
}
