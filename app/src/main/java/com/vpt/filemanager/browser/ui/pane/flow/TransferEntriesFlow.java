package com.vpt.filemanager.browser.ui.pane.flow;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.vpt.filemanager.browser.action.transfer.CancellationToken;
import com.vpt.filemanager.browser.action.transfer.TransferConflictDecision;
import com.vpt.filemanager.browser.action.transfer.TransferConflictResolver;
import com.vpt.filemanager.browser.action.transfer.TransferEntriesAction;
import com.vpt.filemanager.browser.action.transfer.TransferMode;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.ui.dialog.ConflictDialog;
import com.vpt.filemanager.browser.ui.pane.DualPaneHostFragment;

/** Dialog bridge only; transfer behavior remains in TransferEntriesHandler. */
public final class TransferEntriesFlow {
    private final DualPaneHostFragment host;

    public TransferEntriesFlow(DualPaneHostFragment host) {
        this.host = host;
    }

    public void execute(TransferMode mode) {
        CancellationToken cancellation = new CancellationToken();
        host.dispatch(new TransferEntriesAction(host.activePaneId(), host.activePaneId().other(),
                host.activeState().selectedItems(), mode, new Resolver(cancellation), cancellation));
    }

    private final class Resolver implements TransferConflictResolver {
        private final CancellationToken cancellation;
        Resolver(CancellationToken cancellation) { this.cancellation = cancellation; }

        @Override
        public TransferConflictDecision resolve(Item existing, String name) {
            CountDownLatch wait = new CountDownLatch(1);
            AtomicReference<TransferConflictDecision> decision =
                    new AtomicReference<>(TransferConflictDecision.CANCEL);
            host.requireActivity().runOnUiThread(() -> ConflictDialog.show(host.requireContext(), name,
                    new ConflictDialog.OnChoice() {
                        @Override public void onReplace() {
                            decision.set(TransferConflictDecision.REPLACE);
                            wait.countDown();
                        }
                        @Override public void onKeepBoth() {
                            decision.set(TransferConflictDecision.KEEP_BOTH);
                            wait.countDown();
                        }
                        @Override public void onCancel() {
                            cancellation.cancel();
                            wait.countDown();
                        }
                    }));
            try {
                if (!wait.await(300, TimeUnit.SECONDS)) cancellation.cancel();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                cancellation.cancel();
            }
            return decision.get();
        }
    }
}
