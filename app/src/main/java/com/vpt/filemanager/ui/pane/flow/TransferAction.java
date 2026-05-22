package com.vpt.filemanager.ui.pane.flow;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.vpt.filemanager.R;
import com.vpt.filemanager.ui.pane.DualPaneHostFragment;
import com.vpt.filemanager.ui.pane.PaneViewModel;
import com.vpt.filemanager.ui.dialog.ConflictDialog;
import com.vpt.filemanager.event.FileTreeChangeBus;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodeFactory;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.operations.support.NodeFileBackend;
import com.vpt.filemanager.operations.transfer.NameConflict;
import com.vpt.filemanager.operations.transfer.TransferConflictDecision;
import com.vpt.filemanager.operations.transfer.TransferConflictResolver;
import com.vpt.filemanager.operations.transfer.TransferKind;
import com.vpt.filemanager.operations.transfer.TransferOperation;
import com.vpt.filemanager.threading.AppExecutors;

/**
 * Android boundary for cross-pane copy/move.
 *
 * <p>Transfer rules live in {@link TransferOperation}. This class snapshots pane state, resolves
 * virtual nodes, supplies an Android dialog-backed conflict resolver, and posts UI effects.
 */
public final class TransferAction {
    private static final int CONFLICT_WAIT_TIMEOUT_SEC = 300;

    private final DualPaneHostFragment host;
    private final AppExecutors executors;
    private final NodeFactory nodeFactory;
    private final TransferOperation transferOperation;
    private final FileTreeChangeBus changeBus;

    private volatile Future<?> pendingBatch;
    private volatile NodeFileBackend.CancellationToken pendingToken;

    public TransferAction(DualPaneHostFragment host,
                          AppExecutors executors,
                          NodeFactory nodeFactory,
                          TransferOperation transferOperation,
                          FileTreeChangeBus changeBus) {
        this.host = host;
        this.executors = executors;
        this.nodeFactory = nodeFactory;
        this.transferOperation = transferOperation;
        this.changeBus = changeBus;
    }

    public void cancel() {
        NodeFileBackend.CancellationToken token = pendingToken;
        if (token != null) {
            token.cancel();
        }
        Future<?> f = pendingBatch;
        if (f != null) {
            f.cancel(true);
        }
    }

    public void execute(@NonNull TransferMode mode) {
        PaneViewModel activeVm = host.activeVm();
        PaneViewModel inactiveVm = host.inactiveVm();
        Set<NodePath> selection = activeVm.selection().getValue();
        NodePath dstParentPath = inactiveVm.currentPath();
        NodePath srcParentPath = activeVm.currentPath();

        if (selection == null || selection.isEmpty()) {
            return;
        }
        if (dstParentPath == null || srcParentPath == null) {
            host.toast(host.getString(R.string.transfer_no_destination));
            return;
        }
        if (srcParentPath.equals(dstParentPath)) {
            host.toast(host.getString(R.string.transfer_same_folder));
            return;
        }

        List<NodePath> snapshot = List.copyOf(selection);
        activeVm.exitSelectionMode();

        NodeFileBackend.CancellationToken token = new NodeFileBackend.CancellationToken();
        pendingToken = token;
        pendingBatch = executors.io().submit(() -> runTransfer(mode, snapshot, dstParentPath, token));
    }

    private void runTransfer(@NonNull TransferMode mode,
                             @NonNull List<NodePath> snapshot,
                             @NonNull NodePath dstParentPath,
                             @NonNull NodeFileBackend.CancellationToken token) {
        try {
            VirtualNode dstParent;
            try {
                dstParent = nodeFactory.fromPath(dstParentPath);
            } catch (NodeException e) {
                postToast(R.string.transfer_dest_unreachable);
                return;
            }
            if (!dstParent.source().supportsWrite()) {
                postToast(R.string.transfer_dest_readonly);
                return;
            }

            List<VirtualNode> sources = new ArrayList<>(snapshot.size());
            for (NodePath path : snapshot) {
                if (token.isCancelled() || Thread.currentThread().isInterrupted()) {
                    break;
                }
                sources.add(nodeFactory.fromPath(path));
            }

            TransferOperation.Result result = transferOperation.execute(new TransferOperation.Input(
                    sources,
                    dstParent,
                    mode == TransferMode.COPY ? TransferKind.COPY : TransferKind.MOVE,
                    new DialogConflictResolver(token),
                    token));
            changeBus.emit();
            postToast(result.message());
        } catch (NodeException e) {
            postToast(e.getMessage() == null ? "Transfer failed" : e.getMessage());
        } catch (RuntimeException e) {
            timber.log.Timber.e(e, "Transfer batch crashed");
            try {
                changeBus.emit();
            } catch (RuntimeException ignored) {
            }
            postToast("Transfer crashed: "
                    + (e.getMessage() == null ? "unknown" : e.getMessage()));
            throw e;
        } finally {
            pendingBatch = null;
            pendingToken = null;
        }
    }

    private void postToast(int stringResId) {
        executors.main().execute(() -> {
            if (host.isAdded() && host.getActivity() != null) {
                host.toast(host.getString(stringResId));
            }
        });
    }

    private void postToast(@NonNull String msg) {
        executors.main().execute(() -> {
            if (host.isAdded() && host.getActivity() != null) {
                host.toast(msg);
            }
        });
    }

    private final class DialogConflictResolver implements TransferConflictResolver {
        private final NodeFileBackend.CancellationToken token;

        DialogConflictResolver(NodeFileBackend.CancellationToken token) {
            this.token = token;
        }

        @NonNull
        @Override
        public TransferConflictDecision resolve(@NonNull NameConflict conflict) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<TransferConflictDecision> result = new AtomicReference<>(
                    TransferConflictDecision.CANCEL);

            executors.main().execute(() -> {
                if (!host.isAdded() || host.getActivity() == null) {
                    token.cancel();
                    result.set(TransferConflictDecision.CANCEL);
                    latch.countDown();
                    return;
                }
                Context ctx = host.requireContext();
                ConflictDialog.show(ctx, conflict.requestedName, new ConflictDialog.OnChoice() {
                    @Override
                    public void onReplace() {
                        result.set(TransferConflictDecision.REPLACE);
                        latch.countDown();
                    }

                    @Override
                    public void onKeepBoth() {
                        result.set(TransferConflictDecision.KEEP_BOTH);
                        latch.countDown();
                    }

                    @Override
                    public void onCancel() {
                        token.cancel();
                        result.set(TransferConflictDecision.CANCEL);
                        latch.countDown();
                    }
                });
            });

            try {
                if (!latch.await(CONFLICT_WAIT_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                    timber.log.Timber.w("Conflict dialog timeout, treating as cancel");
                    token.cancel();
                    return TransferConflictDecision.CANCEL;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                token.cancel();
                return TransferConflictDecision.CANCEL;
            }
            return result.get();
        }
    }
}
