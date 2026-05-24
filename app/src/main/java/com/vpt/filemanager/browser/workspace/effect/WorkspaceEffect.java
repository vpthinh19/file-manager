package com.vpt.filemanager.browser.workspace.effect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.vpt.filemanager.browser.action.entry.CreateEntryAction;
import com.vpt.filemanager.browser.item.Item;

/** Android-facing work that handlers request but the coordinator does not execute. */
public sealed interface WorkspaceEffect {
    record OpenText(@NonNull String path, @NonNull String displayName, boolean readOnly,
                    @Nullable String archiveEntry) implements WorkspaceEffect {}
    record OpenImage(@NonNull String path) implements WorkspaceEffect {}
    record OpenMedia(@NonNull String path, boolean video) implements WorkspaceEffect {}
    record OpenExternal(@NonNull Item item) implements WorkspaceEffect {}
    record ShowProperties(@NonNull Item item) implements WorkspaceEffect {}
    record Share(@NonNull List<Item> items) implements WorkspaceEffect {
        public Share {
            items = List.copyOf(items);
        }
    }
    record ResolveCreateConflict(@NonNull CreateEntryAction action, @NonNull String name)
            implements WorkspaceEffect {}
    record Toast(@NonNull String message) implements WorkspaceEffect {}
}
