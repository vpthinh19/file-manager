package com.vpt.filemanager.ui.pane.controller;

import com.vpt.filemanager.databinding.FragmentDualPaneHostBinding;
import com.vpt.filemanager.ui.pane.DualPaneHostFragment;
import com.vpt.filemanager.ui.pane.flow.CreateAction;
import com.vpt.filemanager.operations.pane.SwapActivePaneOperation;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.rules.WorkspaceRuleState;
import com.vpt.filemanager.rules.storage.StorageScope;
import com.vpt.filemanager.workspace.WorkspaceAction;

import java.util.Collections;

/**
 * 5-button bottom bar: back / forward / add / swap / up. Extract từ DualPaneHostFragment ở Phase
 * R-5a. Disabled state alpha 0.38 (Material disabled).
 */
public final class BottomBarController {
    private static final float DISABLED_ALPHA = 0.38f;

    private final DualPaneHostFragment host;
    private final FragmentDualPaneHostBinding binding;
    private final CreateAction createAction;
    private final SwapActivePaneOperation swapActivePaneOperation = new SwapActivePaneOperation();

    public BottomBarController(DualPaneHostFragment host,
                                FragmentDualPaneHostBinding binding,
                                CreateAction createAction) {
        this.host = host;
        this.binding = binding;
        this.createAction = createAction;
    }

    public void attach() {
        binding.btnBack.setOnClickListener(v -> host.activeVm().back());
        binding.btnForward.setOnClickListener(v -> host.activeVm().forward());
        binding.btnUp.setOnClickListener(v -> host.activeVm().navigateUp());
        binding.btnAdd.setOnClickListener(v -> createAction.execute());
        binding.btnSwap.setOnClickListener(v -> {
            SwapActivePaneOperation.Output output = swapActivePaneOperation.execute(
                    new SwapActivePaneOperation.Input(
                            DualPaneHostFragment.PANE_LEFT,
                            DualPaneHostFragment.PANE_RIGHT,
                            host.activePaneId()));
            host.onPaneActivated(output.activePaneId);
        });
    }

    public void applyNavButtonState(boolean canBack, boolean canForward) {
        binding.btnBack.setEnabled(canBack);
        binding.btnBack.setAlpha(canBack ? 1f : DISABLED_ALPHA);
        binding.btnForward.setEnabled(canForward);
        binding.btnForward.setAlpha(canForward ? 1f : DISABLED_ALPHA);
    }

    public void applyLocationState(NodePath activePath, NodePath inactivePath) {
        boolean canCreate = !host.disabledActions(WorkspaceRuleState.of(
                Collections.emptySet(), null, activePath, inactivePath))
                .contains(WorkspaceAction.CREATE);
        binding.btnAdd.setEnabled(canCreate);
        binding.btnAdd.setAlpha(canCreate ? 1f : DISABLED_ALPHA);
        boolean canGoUp = StorageScope.canGoUp(activePath);
        binding.btnUp.setEnabled(canGoUp);
        binding.btnUp.setAlpha(canGoUp ? 1f : DISABLED_ALPHA);
    }
}
