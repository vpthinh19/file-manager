package com.vpt.filemanager.browser.action.bookmark;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.data.persistence.BookmarkStore;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class RemoveBookmarksActionHandler implements ActionHandler<RemoveBookmarksAction> {
    private final BookmarkStore bookmarks;
    @Inject public RemoveBookmarksActionHandler(BookmarkStore bookmarks) { this.bookmarks = bookmarks; }
    @Override public ActionResult handle(RemoveBookmarksAction action, WorkspaceSnapshot state) {
        for (Item item : action.items()) bookmarks.remove(item);
        return new ActionResult.Composite(java.util.List.of(
                new ActionResult.ClearSelection(action.pane(), true),
                new ActionResult.RefreshVisible("Bookmark removed")));
    }
}
