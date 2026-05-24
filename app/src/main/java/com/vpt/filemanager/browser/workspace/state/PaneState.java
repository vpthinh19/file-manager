package com.vpt.filemanager.browser.workspace.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.vpt.filemanager.browser.action.browse.SortOrder;
import com.vpt.filemanager.browser.item.Path;
import com.vpt.filemanager.browser.item.Item;

/** Immutable pane DTO rendered by the UI and inspected by handlers/rules. */
public final class PaneState {
    @Nullable public final Path path;
    @NonNull public final List<Item> items;
    @NonNull public final Set<String> selection;
    @NonNull public final SortOrder sortOrder;
    public final boolean loading;
    @Nullable public final String error;
    public final boolean selectionMode;
    public final boolean canGoBack;
    public final boolean canGoForward;
    public final int folderCount;
    public final int fileCount;
    public final long freeBytes;
    public final long totalBytes;

    public PaneState(@Nullable Path path, List<Item> items, Set<String> selection,
                     SortOrder sortOrder, boolean loading, @Nullable String error,
                     boolean selectionMode, boolean canGoBack, boolean canGoForward,
                     int folderCount, int fileCount, long freeBytes, long totalBytes) {
        this.path = path;
        this.items = List.copyOf(items);
        this.selection = Collections.unmodifiableSet(new LinkedHashSet<>(selection));
        this.sortOrder = sortOrder;
        this.loading = loading;
        this.error = error;
        this.selectionMode = selectionMode;
        this.canGoBack = canGoBack;
        this.canGoForward = canGoForward;
        this.folderCount = folderCount;
        this.fileCount = fileCount;
        this.freeBytes = freeBytes;
        this.totalBytes = totalBytes;
    }

    public static PaneState initial(SortOrder sortOrder) {
        return new PaneState(null, List.of(), Set.of(), sortOrder, true, null,
                false, false, false, 0, 0, 0, 0);
    }

    @NonNull
    public List<Item> selectedItems() {
        if (selection.isEmpty()) return List.of();
        return items.stream().filter(item -> selection.contains(item.key())).toList();
    }
}
