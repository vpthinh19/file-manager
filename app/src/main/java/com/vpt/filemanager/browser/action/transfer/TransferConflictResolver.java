package com.vpt.filemanager.browser.action.transfer;

import androidx.annotation.NonNull;

import com.vpt.filemanager.browser.item.Item;

public interface TransferConflictResolver {
    @NonNull TransferConflictDecision resolve(@NonNull Item existing, @NonNull String name);
}
