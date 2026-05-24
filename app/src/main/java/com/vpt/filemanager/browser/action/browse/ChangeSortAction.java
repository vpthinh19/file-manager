package com.vpt.filemanager.browser.action.browse;

import com.vpt.filemanager.browser.action.Action;
import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.workspace.state.PaneId;

public record ChangeSortAction(PaneId pane, SortOrder order) implements Action {
    @Override public ActionKey key() { return ActionKey.SORT; }
}
