package com.vpt.filemanager.browser.action.open;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.workspace.effect.WorkspaceEffect;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class ToolsActionHandler implements ActionHandler<ToolsAction> {
    @Inject public ToolsActionHandler() {}

    @Override
    public ActionResult handle(ToolsAction action, WorkspaceSnapshot state) {
        return new ActionResult.Effect(new WorkspaceEffect.Toast("Tools are not implemented yet"));
    }
}
