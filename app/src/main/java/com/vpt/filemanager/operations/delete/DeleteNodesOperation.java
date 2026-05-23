package com.vpt.filemanager.operations.delete;

import androidx.annotation.NonNull;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.operations.trash.TrashStore;
import com.vpt.filemanager.workspace.MutationResult;

/**
 * Soft-delete virtual nodes by moving them to Trash.
 */
@Singleton
public final class DeleteNodesOperation {
    private final TrashStore trashStore;

    @Inject
    public DeleteNodesOperation(TrashStore trashStore) {
        this.trashStore = trashStore;
    }

    @NonNull
    public Result execute(@NonNull Input input) throws NodeException {
        MutationResult.Builder mutation = MutationResult.builder();
        if (input.nodes.isEmpty()) {
            return new Result(0, 0, null, mutation.build());
        }
        if (!input.continueOnFailure && input.nodes.size() == 1) {
            VirtualNode node = input.nodes.get(0);
            trashStore.moveToTrash(node);
            recordDeletedNode(mutation, node);
            return new Result(1, 0, null, mutation.build());
        }

        int ok = 0;
        int failed = 0;
        String lastError = null;
        for (VirtualNode node : input.nodes) {
            try {
                trashStore.moveToTrash(node);
                recordDeletedNode(mutation, node);
                ok++;
            } catch (NodeException e) {
                if (!input.continueOnFailure) {
                    throw e;
                }
                failed++;
                lastError = e.getMessage();
                timber.log.Timber.w(e, "Delete failed: %s", node.path());
            }
        }
        return new Result(ok, failed, lastError, mutation.build());
    }

    private static void recordDeletedNode(MutationResult.Builder mutation, VirtualNode node) {
        mutation.changedContainer(node.path().parent())
                .changedContainer(NodePath.TRASH_ROOT)
                .removedSubtree(node.path());
    }

    public static final class Input {
        @NonNull public final List<VirtualNode> nodes;
        public final boolean continueOnFailure;

        public Input(@NonNull List<VirtualNode> nodes, boolean continueOnFailure) {
            this.nodes = List.copyOf(nodes);
            this.continueOnFailure = continueOnFailure;
        }
    }

    public static final class Result {
        public final int ok;
        public final int failed;
        public final String lastError;
        @NonNull public final MutationResult mutation;

        private Result(int ok, int failed, String lastError, @NonNull MutationResult mutation) {
            this.ok = ok;
            this.failed = failed;
            this.lastError = lastError;
            this.mutation = mutation;
        }

        @NonNull
        public String message(@NonNull String successVerb) {
            if (failed == 0) {
                return ok + " " + successVerb;
            }
            if (ok == 0) {
                return successVerb.substring(0, 1).toUpperCase() + successVerb.substring(1)
                        + " failed: " + (lastError == null ? "unknown" : lastError);
            }
            return ok + " " + successVerb + ", " + failed + " failed: "
                    + (lastError == null ? "unknown" : lastError);
        }
    }
}
