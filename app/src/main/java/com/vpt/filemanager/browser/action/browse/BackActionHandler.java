package com.vpt.filemanager.browser.action.browse;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class BackActionHandler implements ActionHandler<BackAction> {
    @Inject public BackActionHandler() {}
    @Override public ActionResult handle(BackAction action, WorkspaceSnapshot state) {
        return new ActionResult.History(action.pane(), false);
    }
}
