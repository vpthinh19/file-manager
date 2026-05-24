package com.vpt.filemanager.browser.action.entry;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.workspace.effect.WorkspaceEffect;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class CompressActionHandler implements ActionHandler<CompressAction> {
    @Inject public CompressActionHandler() {}

    @Override
    public ActionResult handle(CompressAction action, WorkspaceSnapshot state) {
        return new ActionResult.Effect(new WorkspaceEffect.Toast("Compression is not implemented yet"));
    }
}
