package com.vpt.filemanager.browser.action.browse;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class ForwardActionHandler implements ActionHandler<ForwardAction> {
    @Inject public ForwardActionHandler() {}
    @Override public ActionResult handle(ForwardAction action, WorkspaceSnapshot state) {
        return new ActionResult.History(action.pane(), true);
    }
}
