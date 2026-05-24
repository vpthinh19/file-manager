package com.vpt.filemanager.browser.action.selection;

import com.vpt.filemanager.browser.action.Action;
import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.workspace.state.PaneId;

public record SelectRangeAction(PaneId pane) implements Action {
    @Override public ActionKey key() { return ActionKey.SELECT_RANGE; }
}
