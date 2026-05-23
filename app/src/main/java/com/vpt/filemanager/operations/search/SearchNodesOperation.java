package com.vpt.filemanager.operations.search;

import androidx.annotation.NonNull;

import javax.inject.Inject;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodePath;

/**
 * Creates a transient virtual result location for a filename query within one node scope.
 *
 * <p>Actual traversal belongs to the {@code search://} source when the workspace materializes the
 * returned location, keeping the operation stateless and allowing refresh/reconciliation.
 */
public final class SearchNodesOperation {
    @Inject
    public SearchNodesOperation() {
    }

    @NonNull
    public Output execute(@NonNull Input input) throws NodeException {
        String query = input.query.trim();
        if (query.isEmpty()) {
            throw new NodeException("Search query is empty");
        }
        NodePath scope = input.scopePath.isSearch()
                ? input.scopePath.searchScope() : input.scopePath;
        return new Output(NodePath.search(scope, query));
    }

    public static final class Input {
        @NonNull public final NodePath scopePath;
        @NonNull public final String query;

        public Input(@NonNull NodePath scopePath, @NonNull String query) {
            this.scopePath = scopePath;
            this.query = query;
        }
    }

    public static final class Output {
        @NonNull public final NodePath resultPath;

        private Output(@NonNull NodePath resultPath) {
            this.resultPath = resultPath;
        }
    }
}
