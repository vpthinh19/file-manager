package com.vpt.filemanager.ui.browser;

import java.util.LinkedHashSet;
import java.util.Set;

import com.vpt.filemanager.domain.model.FileNode;

public final class SelectionTracker {
    private final Set<FileNode> selected = new LinkedHashSet<>();

    public void toggle(FileNode node) {
        if (!selected.remove(node)) {
            selected.add(node);
        }
    }

    public void clear() {
        selected.clear();
    }

    public int count() {
        return selected.size();
    }

    public Set<FileNode> selected() {
        return Set.copyOf(selected);
    }
}

