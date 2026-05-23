package com.vpt.filemanager.operations.bookmark;

import androidx.annotation.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.operations.bookmark.BookmarkStore;
import com.vpt.filemanager.workspace.MutationResult;

/**
 * Add one virtual node to bookmarks.
 */
@Singleton
public final class AddBookmarkOperation {
    private final AddBookmark addBookmark;

    @Inject
    public AddBookmarkOperation(BookmarkStore bookmarkStore) {
        this(bookmarkStore::add);
    }

    public AddBookmarkOperation(AddBookmark addBookmark) {
        this.addBookmark = addBookmark;
    }

    @NonNull
    public Result execute(@NonNull VirtualNode node) throws NodeException {
        addBookmark.add(node);
        return new Result(MutationResult.builder()
                .changedContainer(NodePath.BOOKMARK_ROOT)
                .build());
    }

    @FunctionalInterface
    public interface AddBookmark {
        void add(VirtualNode node) throws NodeException;
    }

    public static final class Result {
        @NonNull public final MutationResult mutation;

        private Result(@NonNull MutationResult mutation) {
            this.mutation = mutation;
        }
    }
}
