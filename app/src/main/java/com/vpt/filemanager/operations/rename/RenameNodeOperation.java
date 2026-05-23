package com.vpt.filemanager.operations.rename;

import androidx.annotation.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.operations.support.NodeFileBackend;
import com.vpt.filemanager.workspace.MutationResult;

/**
 * Rename one virtual node inside its current parent.
 */
@Singleton
public final class RenameNodeOperation {
    private final NodeFileBackend fileBackend;

    @Inject
    public RenameNodeOperation(NodeFileBackend fileBackend) {
        this.fileBackend = fileBackend;
    }

    @NonNull
    public Result execute(@NonNull Input input) throws NodeException {
        VirtualNode renamed = fileBackend.rename(input.node, input.newName);
        return new Result(renamed, MutationResult.builder()
                .changedContainer(input.node.path().parent())
                .removedSubtree(input.node.path())
                .build());
    }

    public static final class Input {
        @NonNull public final VirtualNode node;
        @NonNull public final String newName;

        public Input(@NonNull VirtualNode node, @NonNull String newName) {
            this.node = node;
            this.newName = newName;
        }
    }

    public static final class Result {
        @NonNull public final VirtualNode renamed;
        @NonNull public final MutationResult mutation;

        private Result(@NonNull VirtualNode renamed, @NonNull MutationResult mutation) {
            this.renamed = renamed;
            this.mutation = mutation;
        }
    }
}
