package com.vpt.filemanager.ui.dualpane;

import androidx.annotation.NonNull;

import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.ui.browser.PaneViewModel;

public interface PaneController {
    @NonNull
    PaneViewModel viewModelForPane(@NonNull String paneId);

    void onPaneActivated(@NonNull String paneId);

    void onOpenFile(@NonNull String paneId, @NonNull FileNode node);
}
