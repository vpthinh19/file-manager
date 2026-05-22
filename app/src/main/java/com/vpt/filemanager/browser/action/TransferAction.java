package com.vpt.filemanager.browser.action;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import com.vpt.filemanager.R;
import com.vpt.filemanager.browser.DualPaneHostFragment;
import com.vpt.filemanager.browser.PaneViewModel;
import com.vpt.filemanager.browser.dialog.ConflictDialog;
import com.vpt.filemanager.node.FilePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodeFactory;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.operations.FileOps;
import com.vpt.filemanager.operations.TrashOps;
import com.vpt.filemanager.event.FileTreeChangeBus;
import com.vpt.filemanager.threading.AppExecutors;
import com.vpt.filemanager.util.NameDeconflict;

/**
 * Phase C-1b: bridge giữa SelectionBar COPY/MOVE click → cross-pane transfer.
 *
 * <p><b>Concept MT Manager dual-pane</b>: source = pane đang active, destination = pane còn lại.
 * Không có clipboard intermediate (xem {@code feedback-mt-manager-verify.md}).
 *
 * <p>Flow:
 * <ol>
 *   <li>{@link #execute(TransferMode)} chạy trên main thread — snapshot active selection,
 *       active currentPath, inactive currentPath. Early return + toast nếu degenerate (empty
 *       selection / same folder / inactive missing).</li>
 *   <li>Exit selection mode ngay (theo pattern {@code deleteSelected}) — user thấy mode tắt,
 *       result qua toast cuối batch.</li>
 *   <li>Submit lên {@code executors.io()}: loop từng item, mỗi item:
 *       <ul>
 *         <li>Resolve src VirtualNode.</li>
 *         <li>Check conflict (dst.children() chứa tên đó?) — nếu có → block IO via
 *             {@link CountDownLatch}, post lên main thread show {@link ConflictDialog}, đợi
 *             user chọn Replace / Keep both / Cancel. Cancel = abort toàn batch.</li>
 *         <li>Replace: {@link TrashOps#moveToTrash} entry cũ → {@link FileOps#copy}/move với
 *             tên gốc. Recoverable (entry vào Trash, không hard delete).</li>
 *         <li>Keep both: {@link NameDeconflict#uniqueName} → copy/move với tên unique.</li>
 *         <li>Không conflict: copy/move trực tiếp.</li>
 *       </ul>
 *   </li>
 *   <li>Toast kết quả cuối batch qua {@link DualPaneHostFragment#toast(CharSequence)} +
 *       {@link FileTreeChangeBus#emit()} → cả 2 pane reload (sibling-aware reconciliation).</li>
 * </ol>
 *
 * <p><b>Cancel UI tạm</b>: C-1b không có cancel button rời. User cancel batch qua nút Cancel
 * trong ConflictDialog (khi gặp conflict tiếp theo). Cancel UI thật ship ở C-1c qua foreground
 * notification.
 *
 * <p>Instance-per-Fragment giống {@link CreateAction}; release trong {@code onDestroyView}.
 */
public final class TransferAction {
    private static final int CANCEL_WAIT_TIMEOUT_SEC = 300;

    private final DualPaneHostFragment host;
    private final AppExecutors executors;
    private final NodeFactory nodeFactory;
    private final FileOps fileOps;
    private final TrashOps trashOps;
    private final FileTreeChangeBus changeBus;

    // Phase C-1b fix (Codex review): track in-flight batch + token để onDestroyView có thể cancel
    // — tránh Activity leak khi user rotate/back-press giữa batch dài. Cả 2 field cleanup
    // tự reset null cuối runBatch.
    private volatile Future<?> pendingBatch;
    private volatile FileOps.CancellationToken pendingToken;

    public TransferAction(DualPaneHostFragment host,
                          AppExecutors executors,
                          NodeFactory nodeFactory,
                          FileOps fileOps,
                          TrashOps trashOps,
                          FileTreeChangeBus changeBus) {
        this.host = host;
        this.executors = executors;
        this.nodeFactory = nodeFactory;
        this.fileOps = fileOps;
        this.trashOps = trashOps;
        this.changeBus = changeBus;
    }

    /**
     * Gọi từ {@link DualPaneHostFragment#onDestroyView()} để cancel batch đang chạy. Token signal
     * cooperative cancel (FileOps loop check); future.cancel(true) interrupt thread nếu đang
     * block trong latch.await() của conflict dialog (latch.countDown() trên main thread không
     * bao giờ fire vì Fragment đã detach).
     */
    public void cancel() {
        FileOps.CancellationToken token = pendingToken;
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
        Set<FilePath> selection = activeVm.selection().getValue();
        FilePath dstParentPath = inactiveVm.currentPath();
        FilePath srcParentPath = activeVm.currentPath();

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

        List<FilePath> snapshot = List.copyOf(selection);
        activeVm.exitSelectionMode();

        FileOps.CancellationToken token = new FileOps.CancellationToken();
        pendingToken = token;
        pendingBatch = executors.io().submit(() -> runBatch(mode, snapshot, dstParentPath, token));
    }

    private void runBatch(TransferMode mode, List<FilePath> snapshot, FilePath dstParentPath,
                          FileOps.CancellationToken token) {
        int ok = 0;
        int failed = 0;
        int cancelledRemaining = 0;
        String lastError = null;

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

            for (int i = 0; i < snapshot.size(); i++) {
                if (token.isCancelled() || Thread.currentThread().isInterrupted()) {
                    cancelledRemaining = snapshot.size() - i;
                    break;
                }
                FilePath p = snapshot.get(i);
                try {
                    VirtualNode src = nodeFactory.fromPath(p);
                    String name = src.name();
                    ConflictResolution resolved = resolveConflict(dstParent, name, token);
                    if (resolved.cancelled) {
                        token.cancel();
                        cancelledRemaining = snapshot.size() - i;
                        break;
                    }
                    if (resolved.replaceExisting != null) {
                        trashOps.moveToTrash(resolved.replaceExisting);
                        dstParent = nodeFactory.fromPath(dstParentPath);
                    }
                    String targetName = resolved.finalName;
                    if (mode == TransferMode.COPY) {
                        fileOps.copy(src, dstParent, targetName, token);
                    } else {
                        fileOps.move(src, dstParent, targetName, token);
                    }
                    ok++;
                } catch (NodeException e) {
                    // FileOps wrap mọi IO/Security failure thành NodeException — catch chính
                    // xác mức này. RuntimeException + Error bubble lên outer catch để KHÔNG
                    // mask programming bug (IllegalStateException, NPE, …) thành "item failed".
                    failed++;
                    lastError = e.getMessage();
                    timber.log.Timber.w(e, "Transfer %s failed for %s", mode, p);
                }
            }

            changeBus.emit();
            postToast(formatResult(mode, ok, failed, cancelledRemaining, lastError));
        } catch (RuntimeException crashes) {
            timber.log.Timber.e(crashes, "Transfer batch crashed (programming error)");
            // Defensive: nếu batch chết giữa chừng, vẫn emit bus để pane refresh state hiện có
            // (vài item có thể đã copy/move xong trước crash).
            try {
                changeBus.emit();
            } catch (RuntimeException ignored) {
            }
            postToast("Transfer crashed: "
                    + (crashes.getMessage() == null ? "unknown" : crashes.getMessage()));
            // Re-throw lên ExecutorService để uncaught handler ghi nhận (debug build).
            throw crashes;
        } finally {
            pendingBatch = null;
            pendingToken = null;
        }
    }

    /**
     * Probe conflict trên dstParent: trả về Resolution = direct (no conflict), keep-both
     * (NameDeconflict unique), replace (trash existing), hoặc cancel (abort batch).
     *
     * <p>Block IO thread đến khi user chọn trong ConflictDialog. Phase C-1b fix (Codex review):
     * {@code host.requireContext()} chỉ gọi trên main thread SAU {@code isAdded()} guard —
     * tránh TOCTOU race với fragment teardown khi IO post lên main. Nếu fragment đã detach,
     * latch fire ngay với decision Cancel để batch unblock + outer cancel.
     */
    @NonNull
    private ConflictResolution resolveConflict(VirtualNode dstParent, String baseName,
                                                FileOps.CancellationToken batchToken)
            throws NodeException {
        VirtualNode existing = findChild(dstParent, baseName);
        if (existing == null) {
            return ConflictResolution.direct(baseName);
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ConflictResolution> result = new AtomicReference<>(
                ConflictResolution.cancel());
        VirtualNode dstParentFinal = dstParent;

        executors.main().execute(() -> {
            // Single guard trên main thread — không capture requireContext() từ IO trước.
            if (!host.isAdded() || host.getActivity() == null) {
                result.set(ConflictResolution.cancel());
                latch.countDown();
                return;
            }
            Context ctx = host.requireContext();
            ConflictDialog.show(ctx, baseName, new ConflictDialog.OnChoice() {
                @Override
                public void onReplace() {
                    result.set(ConflictResolution.replace(baseName, existing));
                    latch.countDown();
                }

                @Override
                public void onKeepBoth() {
                    try {
                        String unique = NameDeconflict.uniqueName(dstParentFinal, baseName);
                        result.set(ConflictResolution.direct(unique));
                    } catch (NodeException e) {
                        timber.log.Timber.w(e, "Keep-both unique-name failed for %s", baseName);
                        result.set(ConflictResolution.cancel());
                    }
                    latch.countDown();
                }

                @Override
                public void onCancel() {
                    result.set(ConflictResolution.cancel());
                    latch.countDown();
                }
            });
        });

        try {
            if (!latch.await(CANCEL_WAIT_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)) {
                timber.log.Timber.w("Conflict dialog timeout — treating as cancel");
                return ConflictResolution.cancel();
            }
        } catch (InterruptedException ie) {
            // Future.cancel(true) từ onDestroyView interrupt latch.await() — propagate cancel.
            Thread.currentThread().interrupt();
            batchToken.cancel();
            return ConflictResolution.cancel();
        }
        return result.get();
    }

    private static VirtualNode findChild(VirtualNode parent, String name) throws NodeException {
        for (VirtualNode c : parent.children()) {
            if (c.name().equals(name)) {
                return c;
            }
        }
        return null;
    }

    private void postToast(int stringResId) {
        executors.main().execute(() -> {
            if (host.isAdded() && host.getActivity() != null) {
                host.toast(host.getString(stringResId));
            }
        });
    }

    private String formatResult(TransferMode mode, int ok, int failed, int cancelledRemaining,
                                String lastError) {
        String verb = mode == TransferMode.COPY ? "copied" : "moved";
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

    private void postToast(String msg) {
        executors.main().execute(() -> {
            if (host.isAdded() && host.getActivity() != null) {
                host.toast(msg);
            }
        });
    }

    /** Internal value type kết quả của 1 conflict probe — KHÔNG để leak ra ngoài. */
    private static final class ConflictResolution {
        final String finalName;
        final VirtualNode replaceExisting;
        final boolean cancelled;

        private ConflictResolution(String finalName, VirtualNode replaceExisting, boolean cancelled) {
            this.finalName = finalName;
            this.replaceExisting = replaceExisting;
            this.cancelled = cancelled;
        }

        static ConflictResolution direct(String name) {
            return new ConflictResolution(name, null, false);
        }

        static ConflictResolution replace(String name, VirtualNode existing) {
            return new ConflictResolution(name, existing, false);
        }

        static ConflictResolution cancel() {
            return new ConflictResolution(null, null, true);
        }
    }
}
