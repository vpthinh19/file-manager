package com.vpt.filemanager.browser.ui.pane.controller;

import java.util.EnumSet;

import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.action.browse.BackAction;
import com.vpt.filemanager.browser.action.browse.ForwardAction;
import com.vpt.filemanager.browser.action.browse.SwitchActivePaneAction;
import com.vpt.filemanager.browser.action.browse.UpAction;
import com.vpt.filemanager.browser.action.entry.CreateEntryAction;
import com.vpt.filemanager.browser.action.entry.ExistingNamePolicy;
import com.vpt.filemanager.databinding.FragmentDualPaneHostBinding;
import com.vpt.filemanager.browser.ui.dialog.CreateItemDialog;
import com.vpt.filemanager.browser.ui.pane.DualPaneHostFragment;
import com.vpt.filemanager.browser.workspace.state.PaneState;

public final class BottomBarController {
    private static final float DISABLED_ALPHA = 0.38f;
    private final DualPaneHostFragment host;
    private final FragmentDualPaneHostBinding binding;

    public BottomBarController(DualPaneHostFragment host, FragmentDualPaneHostBinding binding) {
        this.host = host;
        this.binding = binding;
    }

    public void attach() {
        binding.btnBack.setOnClickListener(view -> host.dispatch(new BackAction(host.activePaneId())));
        binding.btnForward.setOnClickListener(view -> host.dispatch(new ForwardAction(host.activePaneId())));
        binding.btnUp.setOnClickListener(view -> host.dispatch(new UpAction(host.activePaneId())));
        binding.btnAdd.setOnClickListener(view -> CreateItemDialog.show(host.requireContext(),
                (folder, name) -> host.dispatch(new CreateEntryAction(host.activePaneId(),
                        folder ? CreateEntryAction.Type.FOLDER : CreateEntryAction.Type.FILE,
                        name, ExistingNamePolicy.FAIL))));
        binding.btnSwap.setOnClickListener(view ->
                host.dispatch(new SwitchActivePaneAction(host.activePaneId())));
    }

    public void render(PaneState active, PaneState inactive, EnumSet<ActionKey> disabled) {
        applyEnabled(binding.btnBack, active.canGoBack);
        applyEnabled(binding.btnForward, active.canGoForward);
        applyEnabled(binding.btnUp, active.path != null && (active.path.isArchive()
                || active.path.isStorage()
                && !active.path.directory().equals(com.vpt.filemanager.browser.rule.StorageBoundary.ROOT_PATH)));
        applyEnabled(binding.btnAdd, !disabled.contains(ActionKey.CREATE));
    }

    private static void applyEnabled(android.view.View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : DISABLED_ALPHA);
    }
}
