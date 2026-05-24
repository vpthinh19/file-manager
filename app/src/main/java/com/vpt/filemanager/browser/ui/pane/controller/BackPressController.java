package com.vpt.filemanager.browser.ui.pane.controller;

import androidx.activity.OnBackPressedCallback;

import com.vpt.filemanager.browser.action.browse.UpAction;
import com.vpt.filemanager.browser.action.selection.ClearSelectionAction;
import com.vpt.filemanager.browser.ui.drawer.DrawerHost;
import com.vpt.filemanager.browser.ui.pane.DualPaneHostFragment;

public final class BackPressController {
    private final DualPaneHostFragment host;
    public BackPressController(DualPaneHostFragment host) { this.host = host; }

    public void attach() {
        host.requireActivity().getOnBackPressedDispatcher().addCallback(host.getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override public void handleOnBackPressed() {
                        if (host.requireActivity() instanceof DrawerHost drawer && drawer.isDrawerOpen()) {
                            drawer.closeDrawer();
                        } else if (host.activeState().selectionMode) {
                            host.dispatch(new ClearSelectionAction(host.activePaneId(), true));
                        } else if (host.activeState().path != null
                                && com.vpt.filemanager.browser.rule.StorageBoundary.canNavigateUp(
                                host.activeState().path)) {
                            host.dispatch(new UpAction(host.activePaneId()));
                        } else {
                            setEnabled(false);
                            host.requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });
    }
}
