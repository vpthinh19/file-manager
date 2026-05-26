package com.vpt.filemanager.component.pane;

import androidx.annotation.Nullable;

import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.core.entry.SortOption;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.storage.facade.Capability;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable rendering state published for one pane component. */
public final class PaneState {
    public final Path location;
    public final List<Entry> entries;
    public final Set<String> selection;
    public final SortOption sort;
    public final boolean loading;
    @Nullable public final String error;
    public final boolean selectionMode;
    public final boolean canGoBack;
    public final boolean canGoForward;
    public final EnumSet<Capability> capabilities;
    public final int folderCount;
    public final int fileCount;

    public PaneState(Path location, List<Entry> entries, Set<String> selection, SortOption sort,
                     boolean loading, @Nullable String error, boolean selectionMode,
                     boolean canGoBack, boolean canGoForward, EnumSet<Capability> capabilities) {
        this.location = location;
        this.entries = List.copyOf(entries);
        this.selection = Collections.unmodifiableSet(new LinkedHashSet<>(selection));
        this.sort = sort;
        this.loading = loading;
        this.error = error;
        this.selectionMode = selectionMode;
        this.canGoBack = canGoBack;
        this.canGoForward = canGoForward;
        this.capabilities = capabilities.clone();
        int folders = 0;
        int files = 0;
        for (Entry entry : entries) {
            if (entry.isParent()) continue;
            if (entry.isFolder()) folders++; else files++;
        }
        folderCount = folders;
        fileCount = files;
    }

    public List<Entry> selectedEntries() {
        if (selection.isEmpty()) return List.of();
        return entries.stream().filter(entry -> selection.contains(entry.key())).toList();
    }
}
