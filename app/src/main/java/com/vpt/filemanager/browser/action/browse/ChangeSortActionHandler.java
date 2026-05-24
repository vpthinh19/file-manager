package com.vpt.filemanager.browser.action.browse;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.data.persistence.UserPreferences;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class ChangeSortActionHandler implements ActionHandler<ChangeSortAction> {
    private final UserPreferences preferences;
    @Inject public ChangeSortActionHandler(UserPreferences preferences) { this.preferences = preferences; }
    @Override public ActionResult handle(ChangeSortAction action, WorkspaceSnapshot state) {
        preferences.setSortOrder(action.order());
        return new ActionResult.Sort(action.pane(), action.order());
    }
}
