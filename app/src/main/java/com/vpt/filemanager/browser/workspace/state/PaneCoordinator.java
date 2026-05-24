package com.vpt.filemanager.browser.workspace.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.vpt.filemanager.browser.action.browse.SortOrder;
import com.vpt.filemanager.browser.item.Path;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.rule.StorageBoundary;

/** Mutable implementation detail owned exclusively by WorkspaceCoordinator. */
public final class PaneCoordinator {
    private final ArrayDeque<Path> back = new ArrayDeque<>();
    private final ArrayDeque<Path> forward = new ArrayDeque<>();
    private final LinkedHashSet<String> selection = new LinkedHashSet<>();
    private Path path;
    private List<Item> items = List.of();
    private SortOrder sort;
    private boolean loading;
    private String error;
    private boolean selectionMode;
    private long generation;

    public PaneCoordinator(SortOrder sort) {
        this.sort = sort;
    }

    @Nullable public Path location() { return path; }
    public SortOrder sort() { return sort; }

    public void navigate(Path target) {
        if (target.equals(path)) return;
        if (path != null) back.push(path);
        forward.clear();
        setLocation(target);
    }

    public void open(Path target) {
        setLocation(target);
    }

    public boolean back() {
        if (back.isEmpty()) return false;
        if (path != null) forward.push(path);
        setLocation(back.pop());
        return true;
    }

    public boolean forward() {
        if (forward.isEmpty()) return false;
        if (path != null) back.push(path);
        setLocation(forward.pop());
        return true;
    }

    public void sort(SortOrder sort) {
        this.sort = sort;
    }

    public long beginLoad() {
        loading = true;
        error = null;
        return ++generation;
    }

    public void loaded(long request, List<Item> loaded) {
        if (request != generation) return;
        loading = false;
        error = null;
        items = List.copyOf(loaded);
        selection.retainAll(items.stream().map(Item::key).toList());
    }

    public void failed(long request, String message) {
        if (request != generation) return;
        loading = false;
        error = message;
        items = List.of();
        selection.clear();
    }

    @Nullable
    public Path recoverDeletedStorageLocation() {
        if (path != null && path.isArchive() && !new File(path.container()).isFile()) {
            Path recovery = path.parent();
            while (recovery != null && recovery.isArchive()) recovery = recovery.parent();
            if (recovery != null) {
                setLocation(recovery);
                return recovery;
            }
        }
        if (path == null || !path.isStorage()) return null;
        Path candidate = path;
        while (StorageBoundary.canNavigateUp(candidate)) {
            candidate = candidate.parent();
            if (new File(candidate.directory()).isDirectory()) {
                setLocation(candidate);
                return candidate;
            }
        }
        if (!path.equals(StorageBoundary.root())) {
            setLocation(StorageBoundary.root());
            return StorageBoundary.root();
        }
        return null;
    }

    public void toggle(Item item, boolean enterMode) {
        if (item.isParent()) return;
        if (enterMode) selectionMode = true;
        if (!selectionMode) return;
        if (!selection.add(item.key())) selection.remove(item.key());
    }

    public void selectAll() {
        selectionMode = true;
        selection.clear();
        for (Item item : items) if (!item.isParent()) selection.add(item.key());
    }

    public void selectRange() {
        if (selection.size() < 2) return;
        int first = Integer.MAX_VALUE;
        int last = -1;
        for (int index = 0; index < items.size(); index++) {
            if (selection.contains(items.get(index).key())) {
                first = Math.min(first, index);
                last = Math.max(last, index);
            }
        }
        for (int index = first; index <= last && index < items.size(); index++) {
            if (!items.get(index).isParent()) selection.add(items.get(index).key());
        }
    }

    public void clear(boolean exitMode) {
        selection.clear();
        if (exitMode) selectionMode = false;
    }

    public PaneState snapshot() {
        int folders = 0;
        int files = 0;
        for (Item item : items) {
            if (item.isParent()) continue;
            if (item.isFolder()) folders++; else files++;
        }
        long total = 0;
        long free = 0;
        if (path != null && path.isStorage()) {
            File disk = new File(path.directory());
            total = disk.getTotalSpace();
            free = disk.getFreeSpace();
        }
        return new PaneState(path, items, Set.copyOf(selection), sort, loading, error,
                selectionMode, !back.isEmpty(), !forward.isEmpty(), folders, files, free, total);
    }

    private void setLocation(@NonNull Path path) {
        this.path = path;
        selection.clear();
        selectionMode = false;
    }
}
