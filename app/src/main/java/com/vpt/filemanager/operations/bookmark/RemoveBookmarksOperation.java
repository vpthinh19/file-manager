package com.vpt.filemanager.operations.bookmark;

import androidx.annotation.NonNull;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.operations.BatchResult;
import com.vpt.filemanager.operations.BookmarkOps;

/**
 * Remove bookmark rows by local path.
 */
@Singleton
public final class RemoveBookmarksOperation {
    private final RemoveBookmark removeBookmark;

    @Inject
    public RemoveBookmarksOperation(BookmarkOps bookmarkOps) {
        this(bookmarkOps::removeByPath);
    }

    public RemoveBookmarksOperation(RemoveBookmark removeBookmark) {
        this.removeBookmark = removeBookmark;
    }

    @NonNull
    public BatchResult execute(@NonNull List<NodePath> paths) {
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
        return new BatchResult(ok, failed, lastError);
    }

    @FunctionalInterface
    public interface RemoveBookmark {
        void remove(NodePath path);
    }
}
