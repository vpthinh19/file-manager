package com.vpt.filemanager.operations.bookmark;

import androidx.annotation.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.operations.BookmarkOps;

/**
 * Add one virtual node to bookmarks.
 */
@Singleton
public final class AddBookmarkOperation {
    private final AddBookmark addBookmark;

    @Inject
    public AddBookmarkOperation(BookmarkOps bookmarkOps) {
        this(bookmarkOps::add);
    }

    public AddBookmarkOperation(AddBookmark addBookmark) {
        this.addBookmark = addBookmark;
    }

    public void execute(@NonNull VirtualNode node) throws NodeException {
        addBookmark.add(node);
    }

    @FunctionalInterface
    public interface AddBookmark {
        void add(VirtualNode node) throws NodeException;
    }
}
