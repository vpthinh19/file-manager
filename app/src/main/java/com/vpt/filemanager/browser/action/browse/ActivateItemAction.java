package com.vpt.filemanager.browser.action.browse;

import com.vpt.filemanager.browser.action.Action;
import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.workspace.state.PaneId;

public record ActivateItemAction(PaneId pane, Item item) implements Action {
    @Override public ActionKey key() { return ActionKey.ACTIVATE_ITEM; }
}
