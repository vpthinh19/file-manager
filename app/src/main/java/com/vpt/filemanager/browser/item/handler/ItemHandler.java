package com.vpt.filemanager.browser.item.handler;

import androidx.annotation.NonNull;

import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.workspace.state.PaneId;
import com.vpt.filemanager.core.error.FileOperationException;

public interface ItemHandler {
    @NonNull ActionResult activate(@NonNull PaneId pane, @NonNull Item item)
            throws FileOperationException;
}
