package com.vpt.filemanager.browser.action.open;

import java.util.List;

import com.vpt.filemanager.browser.action.Action;
import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.workspace.state.PaneId;

public record ToolsAction(PaneId pane, List<Item> items) implements Action {
    public ToolsAction { items = List.copyOf(items); }
    @Override public ActionKey key() { return ActionKey.TOOLS; }
}
