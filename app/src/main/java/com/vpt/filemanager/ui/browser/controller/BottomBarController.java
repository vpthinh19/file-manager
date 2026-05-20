package com.vpt.filemanager.ui.browser.controller;

import com.vpt.filemanager.databinding.FragmentDualPaneHostBinding;
import com.vpt.filemanager.ui.browser.DualPaneHostFragment;
import com.vpt.filemanager.ui.browser.action.CreateAction;

/**
 * 5-button bottom bar: back / forward / add / swap / up. Extract từ DualPaneHostFragment ở Phase
 * R-5a. Disabled state alpha 0.38 (Material disabled).
 */
public final class BottomBarController {
    private static final float DISABLED_ALPHA = 0.38f;

    private final DualPaneHostFragment host;
    private final FragmentDualPaneHostBinding binding;
    private final CreateAction createAction;

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
            String next = DualPaneHostFragment.PANE_LEFT.equals(host.activePaneId())
                    ? DualPaneHostFragment.PANE_RIGHT
                    : DualPaneHostFragment.PANE_LEFT;
            host.onPaneActivated(next);
        });
    }

    public void applyNavButtonState(boolean canBack, boolean canForward) {
        binding.btnBack.setEnabled(canBack);
        binding.btnBack.setAlpha(canBack ? 1f : DISABLED_ALPHA);
        binding.btnForward.setEnabled(canForward);
        binding.btnForward.setAlpha(canForward ? 1f : DISABLED_ALPHA);
    }
}
