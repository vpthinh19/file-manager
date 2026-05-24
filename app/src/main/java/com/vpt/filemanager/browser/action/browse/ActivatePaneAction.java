package com.vpt.filemanager.browser.action.browse;

import com.vpt.filemanager.browser.action.Action;
import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.workspace.state.PaneId;

public record ActivatePaneAction(PaneId pane) implements Action {
    @Override public ActionKey key() { return ActionKey.ACTIVATE_PANE; }
}
