package com.vpt.filemanager.browser.action.trash;

import com.vpt.filemanager.browser.action.Action;
import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.workspace.state.PaneId;

public record EmptyTrashAction(PaneId pane) implements Action {
    @Override public ActionKey key() { return ActionKey.EMPTY_TRASH; }
}
