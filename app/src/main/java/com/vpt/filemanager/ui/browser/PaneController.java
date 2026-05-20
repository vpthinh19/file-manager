package com.vpt.filemanager.ui.browser;

import androidx.annotation.NonNull;

import com.vpt.filemanager.node.VirtualNode;

/**
 * Callback từ PaneFragment lên host (DualPaneHostFragment).
 *
 * <p>Phase R-5b migrated {@link #onOpenFile(String, VirtualNode)} từ FileNode → VirtualNode.
 * Host dispatch click qua {@link com.vpt.filemanager.opener.OpenerRegistry} thay vì
 * {@code FileOpener.decide} enum.
 */
public interface PaneController {
    @NonNull
    PaneViewModel viewModelForPane(@NonNull String paneId);

    void onPaneActivated(@NonNull String paneId);

    void onOpenFile(@NonNull String paneId, @NonNull VirtualNode node);
}
