package com.vpt.filemanager.browser.action.browse;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.browser.item.Path;
import com.vpt.filemanager.browser.workspace.state.PaneState;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class SearchActionHandler implements ActionHandler<SearchAction> {
    @Inject public SearchActionHandler() {}
    @Override public ActionResult handle(SearchAction action, WorkspaceSnapshot state)
            throws FileOperationException {
        PaneState pane = state.pane(action.pane());
        Path path = pane.path;
        if (path == null || (!path.isStorage() && !path.isSearch())) {
            throw new FileOperationException("Search is available in storage folders");
        }
        return new ActionResult.Navigate(action.pane(),
                Path.search(path.directory(), action.query()));
    }
}
