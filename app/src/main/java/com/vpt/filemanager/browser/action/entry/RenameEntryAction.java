package com.vpt.filemanager.browser.action.entry;

import com.vpt.filemanager.browser.action.Action;
import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.workspace.state.PaneId;

public record RenameEntryAction(PaneId pane, Item item, String name) implements Action {
    @Override public ActionKey key() { return ActionKey.RENAME; }
}
