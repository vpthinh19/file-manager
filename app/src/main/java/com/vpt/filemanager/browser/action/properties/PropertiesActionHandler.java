package com.vpt.filemanager.browser.action.properties;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.workspace.effect.WorkspaceEffect;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class PropertiesActionHandler implements ActionHandler<PropertiesAction> {
    @Inject public PropertiesActionHandler() {}
    @Override public ActionResult handle(PropertiesAction action, WorkspaceSnapshot state) {
        return new ActionResult.Effect(new WorkspaceEffect.ShowProperties(action.item()));
    }
}
