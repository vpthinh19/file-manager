package com.vpt.filemanager.browser.action.entry;

import com.vpt.filemanager.browser.action.Action;
import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.workspace.state.PaneId;

public record CreateEntryAction(PaneId pane, Type type, String name, ExistingNamePolicy policy)
        implements Action {
    public enum Type { FILE, FOLDER }
    @Override public ActionKey key() { return ActionKey.CREATE; }
}
