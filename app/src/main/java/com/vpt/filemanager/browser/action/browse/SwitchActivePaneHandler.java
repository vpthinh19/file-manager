package com.vpt.filemanager.browser.action.browse;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class SwitchActivePaneHandler implements ActionHandler<SwitchActivePaneAction> {
    @Inject public SwitchActivePaneHandler() {}

    @Override
    public ActionResult handle(SwitchActivePaneAction action, WorkspaceSnapshot state) {
        return new ActionResult.Activate(action.pane().other());
    }
}
