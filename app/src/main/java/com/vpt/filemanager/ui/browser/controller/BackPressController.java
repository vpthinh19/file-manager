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
 *   <li>Pane đang selection mode → exit mode hẳn (back = X button)</li>
 *   <li>Pane navigateUp() success → stay in app</li>
 *   <li>Else → setEnabled(false) + dispatch system back (exit/up activity stack)</li>
 * </ol>
 *
 * <p>Phase R-7a fix: trước đây gọi {@code vm.clearSelection()} — sau khi mode flag tách khỏi
 * selection set, clearSelection chỉ clear items mà giữ mode active → back press không thể
 * thoát mode → user stuck. Đổi sang {@link PaneViewModel#exitSelectionMode()} để back press
 * = X button semantics.
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
                            vm.exitSelectionMode();
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
