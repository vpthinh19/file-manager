package com.vpt.filemanager.browser.action.trash;

import java.util.List;

import com.vpt.filemanager.browser.action.Action;
import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.workspace.state.PaneId;

public record RestoreTrashAction(PaneId pane, List<Item> items) implements Action {
    public RestoreTrashAction { items = List.copyOf(items); }
    @Override public ActionKey key() { return ActionKey.RESTORE_TRASH; }
}
