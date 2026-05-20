package com.vpt.filemanager.ui.browser.controller;

import androidx.activity.OnBackPressedCallback;

import com.vpt.filemanager.ui.DrawerHost;
import com.vpt.filemanager.ui.browser.DualPaneHostFragment;
import com.vpt.filemanager.ui.browser.PaneViewModel;

/**
 * Back press chain: drawer > selection mode > pane navigation > system. Extract từ
 * DualPaneHostFragment.installBackHandler ở Phase R-5a.
 *
 * <p>Priority:
 * <ol>
 *   <li>Drawer mở → close drawer</li>
 *   <li>Pane đang selection mode → clear selection</li>
 *   <li>Pane navigateUp() success → stay in app</li>
 *   <li>Else → setEnabled(false) + dispatch system back (exit/up activity stack)</li>
 * </ol>
 */
public final class BackPressController {
    private final DualPaneHostFragment host;

    public BackPressController(DualPaneHostFragment host) {
        this.host = host;
    }

    public void attach() {
        host.requireActivity().getOnBackPressedDispatcher().addCallback(
                host.getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (host.requireActivity() instanceof DrawerHost dh && dh.isDrawerOpen()) {
                            dh.closeDrawer();
                            return;
                        }
                        PaneViewModel vm = host.activeVm();
                        if (vm.isInSelectionMode()) {
                            vm.clearSelection();
                            return;
                        }
                        if (!vm.navigateUp()) {
                            setEnabled(false);
                            host.requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });
    }
}
