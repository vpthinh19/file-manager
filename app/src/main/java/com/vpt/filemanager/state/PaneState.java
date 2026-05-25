package com.vpt.filemanager.state;

import androidx.annotation.Nullable;

import com.vpt.filemanager.model.Entry;
import com.vpt.filemanager.model.Location;
import com.vpt.filemanager.model.SortOption;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable public pane state observed by independent UI components. */
public final class PaneState {
    public final Location location;
    public final List<Entry> entries;
    public final Set<String> selection;
    public final SortOption sort;
    public final boolean loading;
    @Nullable public final String error;
    public final boolean selectionMode;
    public final boolean canGoBack;
    public final boolean canGoForward;
    public final int folderCount;
    public final int fileCount;
    public final long freeBytes;
    public final long totalBytes;

    PaneState(Location location, List<Entry> entries, Set<String> selection, SortOption sort,
              boolean loading, @Nullable String error, boolean selectionMode,
              boolean canGoBack, boolean canGoForward) {
        this.location = location;
        this.entries = List.copyOf(entries);
        this.selection = Collections.unmodifiableSet(new LinkedHashSet<>(selection));
        this.sort = sort;
        this.loading = loading;
        this.error = error;
        this.selectionMode = selectionMode;
        this.canGoBack = canGoBack;
        this.canGoForward = canGoForward;
        int folders = 0;
        int files = 0;
        for (Entry entry : entries) {
            if (entry.isParent()) continue;
            if (entry.isFolder()) folders++; else files++;
        }
        folderCount = folders;
        fileCount = files;
        if (location.isStorage() && !location.isArchiveEntry()) {
            File disk = new File(location.physicalPath());
            freeBytes = disk.getFreeSpace();
            totalBytes = disk.getTotalSpace();
        } else {
            freeBytes = 0;
            totalBytes = 0;
        }
    }

    public List<Entry> selectedEntries() {
        if (selection.isEmpty()) return List.of();
        return entries.stream().filter(entry -> selection.contains(entry.key())).toList();
    }
}
