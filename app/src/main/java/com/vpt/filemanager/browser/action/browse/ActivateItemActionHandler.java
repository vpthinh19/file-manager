package com.vpt.filemanager.browser.action.browse;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.item.handler.ItemHandlerRegistry;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;
import com.vpt.filemanager.core.error.FileOperationException;

public final class ActivateItemActionHandler implements ActionHandler<ActivateItemAction> {
    private final ItemHandlerRegistry taps;
    @Inject public ActivateItemActionHandler(ItemHandlerRegistry taps) { this.taps = taps; }
    @Override public ActionResult handle(ActivateItemAction action, WorkspaceSnapshot state)
            throws FileOperationException {
        return taps.activate(action.pane(), action.item());
    }
}
