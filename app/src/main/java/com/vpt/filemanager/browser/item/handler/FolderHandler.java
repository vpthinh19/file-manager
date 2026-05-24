package com.vpt.filemanager.browser.item.handler;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.workspace.state.PaneId;

public final class FolderHandler implements ItemHandler {
    @Inject public FolderHandler() {}
    @Override public ActionResult activate(PaneId pane, Item item) {
        return new ActionResult.Navigate(pane, item.target());
    }
}
