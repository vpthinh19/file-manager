package com.vpt.filemanager.browser.action.browse;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class ActivatePaneActionHandler implements ActionHandler<ActivatePaneAction> {
    @Inject public ActivatePaneActionHandler() {}
    @Override public ActionResult handle(ActivatePaneAction action, WorkspaceSnapshot state) {
        return new ActionResult.Activate(action.pane());
    }
}
