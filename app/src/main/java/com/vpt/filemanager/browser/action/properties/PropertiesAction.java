package com.vpt.filemanager.browser.action.properties;

import com.vpt.filemanager.browser.action.Action;
import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.workspace.state.PaneId;

public record PropertiesAction(PaneId pane, Item item) implements Action {
    @Override public ActionKey key() { return ActionKey.PROPERTIES; }
}
