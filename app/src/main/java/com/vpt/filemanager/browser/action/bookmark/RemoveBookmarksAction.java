package com.vpt.filemanager.browser.action.bookmark;

import java.util.List;

import com.vpt.filemanager.browser.action.Action;
import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.workspace.state.PaneId;

public record RemoveBookmarksAction(PaneId pane, List<Item> items) implements Action {
    public RemoveBookmarksAction { items = List.copyOf(items); }
    @Override public ActionKey key() { return ActionKey.REMOVE_BOOKMARK; }
}
