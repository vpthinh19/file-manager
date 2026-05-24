package com.vpt.filemanager.browser.action.selection;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class SelectRangeActionHandler implements ActionHandler<SelectRangeAction> {
    @Inject public SelectRangeActionHandler() {}
    @Override public ActionResult handle(SelectRangeAction action, WorkspaceSnapshot state) {
        return new ActionResult.SelectRange(action.pane());
    }
}
