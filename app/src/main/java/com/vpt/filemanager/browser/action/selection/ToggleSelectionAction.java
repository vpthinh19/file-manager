package com.vpt.filemanager.browser.action.selection;

import com.vpt.filemanager.browser.action.Action;
import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.workspace.state.PaneId;

public record ToggleSelectionAction(PaneId pane, Item item, boolean enterMode)
        implements Action {
    @Override public ActionKey key() { return ActionKey.SELECT; }
}
