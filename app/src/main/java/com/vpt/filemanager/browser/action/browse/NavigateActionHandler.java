package com.vpt.filemanager.browser.action.browse;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class NavigateActionHandler implements ActionHandler<NavigateAction> {
    @Inject public NavigateActionHandler() {}
    @Override public ActionResult handle(NavigateAction action, WorkspaceSnapshot state) {
        return new ActionResult.Navigate(action.pane(), action.path());
    }
}
