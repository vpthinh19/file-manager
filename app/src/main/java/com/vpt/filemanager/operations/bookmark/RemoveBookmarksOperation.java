package com.vpt.filemanager.operations.bookmark;

import androidx.annotation.NonNull;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.operations.result.BatchResult;
import com.vpt.filemanager.operations.bookmark.BookmarkStore;
import com.vpt.filemanager.workspace.MutationResult;

/**
 * Remove bookmark rows by local path.
 */
@Singleton
public final class RemoveBookmarksOperation {
    private final RemoveBookmark removeBookmark;

    @Inject
    public RemoveBookmarksOperation(BookmarkStore bookmarkStore) {
        this(bookmarkStore::removeByPath);
    }

    public RemoveBookmarksOperation(RemoveBookmark removeBookmark) {
        this.removeBookmark = removeBookmark;
    }

    @NonNull
    public Result execute(@NonNull List<NodePath> paths) {
        int ok = 0;
        int failed = 0;
        String lastError = null;
        for (NodePath path : paths) {
            try {
                removeBookmark.remove(path);
                ok++;
            } catch (RuntimeException e) {
                failed++;
                lastError = e.getMessage();
                timber.log.Timber.w(e, "Bookmark remove failed: %s", path);
            }
        }
        return new Result(new BatchResult(ok, failed, lastError), MutationResult.builder()
                .changedContainer(NodePath.BOOKMARK_ROOT)
                .build());
    }

    @FunctionalInterface
    public interface RemoveBookmark {
        void remove(NodePath path);
    }

    public static final class Result {
        @NonNull public final BatchResult batch;
        @NonNull public final MutationResult mutation;

        private Result(@NonNull BatchResult batch, @NonNull MutationResult mutation) {
            this.batch = batch;
            this.mutation = mutation;
        }
    }
}
