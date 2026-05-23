package com.vpt.filemanager.operations.navigation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.rules.storage.StorageScope;

/**
 * Resolve the parent path for a pane/node location.
 */
public final class NavigateToParentOperation {
    public Output execute(Input input) {
        NodePath path = input.path;
        if (path == null || path.isRoot()) {
            return new Output(null);
        }
        if (StorageScope.isAtRoot(path)
                || path.equals(NodePath.TRASH_ROOT)
                || path.equals(NodePath.BOOKMARK_ROOT)) {
            return new Output(NodePath.ROOT);
        }
        if (path.isArchive() && "/".equals(path.path())) {
            NodePath archiveFile = NodePath.parse(path.authority());
            return new Output(archiveFile.parent());
        }
        return new Output(path.parent());
    }

    public static final class Input {
        @Nullable public final NodePath path;

        public Input(@Nullable NodePath path) {
            this.path = path;
        }
    }

    public static final class Output {
        @Nullable public final NodePath parentPath;

        private Output(@Nullable NodePath parentPath) {
            this.parentPath = parentPath;
        }
    }
}
