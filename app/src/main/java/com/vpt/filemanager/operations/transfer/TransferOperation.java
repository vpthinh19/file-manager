package com.vpt.filemanager.operations.transfer;

import androidx.annotation.NonNull;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.operations.support.NodeFileBackend;
import com.vpt.filemanager.operations.conflict.UniqueNameGenerator;
import com.vpt.filemanager.operations.trash.TrashStore;
import com.vpt.filemanager.workspace.MutationResult;

/**
 * Batch copy/move operation over virtual nodes.
 *
 * <p>Input and output are UI-free. Conflict handling is delegated through
 * {@link TransferConflictResolver}; this keeps dialogs, workers, and tests pluggable without
 * changing transfer behavior.
 */
@Singleton
public final class TransferOperation {
    private final NodeFileBackend fileBackend;
    private final TrashStore trashStore;

    @Inject
    public TransferOperation(NodeFileBackend fileBackend, TrashStore trashStore) {
        this.fileBackend = fileBackend;
        this.trashStore = trashStore;
    }

    @NonNull
    public Result execute(@NonNull Input input) throws NodeException {
        if (!input.targetParent.source().supportsWrite()) {
            throw new NodeException("Destination is read-only");
        }

        int ok = 0;
        int failed = 0;
        int cancelledRemaining = 0;
        String lastError = null;
        MutationResult.Builder mutation = MutationResult.builder();

        for (int i = 0; i < input.sources.size(); i++) {
            if (input.token.isCancelled() || Thread.currentThread().isInterrupted()) {
                cancelledRemaining = input.sources.size() - i;
                break;
            }
            VirtualNode src = input.sources.get(i);
            try {
                String targetName = resolveTargetName(input.targetParent, src.name(),
                        input.resolver, input.token, mutation);
                if (input.token.isCancelled()) {
                    cancelledRemaining = input.sources.size() - i;
                    break;
                }
                if (input.kind == TransferKind.COPY) {
                    fileBackend.copy(src, input.targetParent, targetName, input.token);
                } else {
                    fileBackend.move(src, input.targetParent, targetName, input.token);
                }
                mutation.changedContainer(input.targetParent.path());
                if (input.kind == TransferKind.MOVE) {
                    mutation.changedContainer(src.path().parent())
                            .removedSubtree(src.path());
                }
                ok++;
            } catch (NodeException e) {
                failed++;
                lastError = e.getMessage();
                timber.log.Timber.w(e, "Transfer %s failed for %s", input.kind, src.path());
            }
        }
        return new Result(input.kind, ok, failed, cancelledRemaining, lastError, mutation.build());
    }

    @NonNull
    private String resolveTargetName(@NonNull VirtualNode targetParent,
                                     @NonNull String requestedName,
                                     @NonNull TransferConflictResolver resolver,
                                     @NonNull NodeFileBackend.CancellationToken token,
                                     @NonNull MutationResult.Builder mutation)
            throws NodeException {
        VirtualNode existing = findChild(targetParent, requestedName);
        if (existing == null) {
            return requestedName;
        }

        TransferConflictDecision decision = resolver.resolve(
                new NameConflict(targetParent, existing, requestedName));
        if (decision == TransferConflictDecision.CANCEL) {
            token.cancel();
            return requestedName;
        }
        if (decision == TransferConflictDecision.REPLACE) {
            if (existing.path().isArchive()) {
                fileBackend.delete(existing);
            } else {
                trashStore.moveToTrash(existing);
                mutation.changedContainer(NodePath.TRASH_ROOT);
            }
            mutation.changedContainer(targetParent.path())
                    .removedSubtree(existing.path());
            return requestedName;
        }
        return UniqueNameGenerator.uniqueName(targetParent, requestedName);
    }

    private static VirtualNode findChild(VirtualNode parent, String name) throws NodeException {
        for (VirtualNode child : parent.children()) {
            if (child.name().equals(name)) {
                return child;
            }
        }
        return null;
    }

    public static final class Input {
        @NonNull public final List<VirtualNode> sources;
        @NonNull public final VirtualNode targetParent;
        @NonNull public final TransferKind kind;
        @NonNull public final TransferConflictResolver resolver;
        @NonNull public final NodeFileBackend.CancellationToken token;

        public Input(@NonNull List<VirtualNode> sources,
                     @NonNull VirtualNode targetParent,
                     @NonNull TransferKind kind,
                     @NonNull TransferConflictResolver resolver,
                     @NonNull NodeFileBackend.CancellationToken token) {
            this.sources = List.copyOf(sources);
            this.targetParent = targetParent;
            this.kind = kind;
            this.resolver = resolver;
            this.token = token;
        }
    }

    public static final class Result {
        @NonNull public final TransferKind kind;
        public final int ok;
        public final int failed;
        public final int cancelledRemaining;
        public final String lastError;
        @NonNull public final MutationResult mutation;

        private Result(@NonNull TransferKind kind, int ok, int failed, int cancelledRemaining,
                       String lastError, @NonNull MutationResult mutation) {
            this.kind = kind;
            this.ok = ok;
            this.failed = failed;
            this.cancelledRemaining = cancelledRemaining;
            this.lastError = lastError;
            this.mutation = mutation;
        }

        @NonNull
        public String message() {
            String verb = kind == TransferKind.COPY ? "copied" : "moved";
            StringBuilder sb = new StringBuilder();
            if (ok > 0) {
                sb.append(ok).append(' ').append(verb);
            }
            if (failed > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(failed).append(" failed");
                if (lastError != null) sb.append(": ").append(lastError);
            }
            if (cancelledRemaining > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(cancelledRemaining).append(" cancelled");
            }
            if (sb.length() == 0) {
                sb.append("Nothing transferred");
            }
            return sb.toString();
        }
    }
}
