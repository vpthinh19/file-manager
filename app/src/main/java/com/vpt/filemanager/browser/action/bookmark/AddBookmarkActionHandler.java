package com.vpt.filemanager.browser.action.bookmark;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.data.persistence.BookmarkStore;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class AddBookmarkActionHandler implements ActionHandler<AddBookmarkAction> {
    private final BookmarkStore bookmarks;
    @Inject public AddBookmarkActionHandler(BookmarkStore bookmarks) { this.bookmarks = bookmarks; }
    @Override public ActionResult handle(AddBookmarkAction action, WorkspaceSnapshot state)
            throws FileOperationException {
        bookmarks.add(action.item());
        return new ActionResult.Composite(java.util.List.of(
                new ActionResult.ClearSelection(action.pane(), true),
                new ActionResult.RefreshVisible("Bookmarked")));
    }
}
