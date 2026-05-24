package com.vpt.filemanager.browser.action.selection;

import com.vpt.filemanager.browser.action.Action;
import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.workspace.state.PaneId;

public record ClearSelectionAction(PaneId pane, boolean exitMode) implements Action {
    @Override public ActionKey key() { return exitMode ? ActionKey.EXIT_SELECTION : ActionKey.CLEAR_SELECTION; }
}
