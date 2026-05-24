package com.vpt.filemanager.browser.workspace.reconcile;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.vpt.filemanager.browser.item.Path;

public final class ChangeSet {
    private final Set<Path> paths;
    private final Set<String> removed;

    public ChangeSet(Set<Path> paths, Set<String> removed) {
        this.paths = Collections.unmodifiableSet(new LinkedHashSet<>(paths));
        this.removed = Collections.unmodifiableSet(new LinkedHashSet<>(removed));
    }

    public boolean affects(Path path) {
        if (paths.contains(path)) return true;
        if (path.isSearch()) {
            for (Path changed : paths) {
                if (changed.isStorage() && intersects(path.directory(), changed.directory())) return true;
            }
        }
        if (path.isArchive()) {
            int separator = path.container().lastIndexOf('/');
            String containerParent = separator <= 0 ? "/" : path.container().substring(0, separator);
            for (Path changed : paths) {
                if (changed.isStorage() && changed.directory().equals(containerParent)) return true;
            }
        }
        if (path.isStorage()) {
            for (String deleted : removed) if (descendant(path.directory(), deleted)) return true;
        }
        return false;
    }

    private static boolean intersects(String first, String second) {
        return descendant(first, second) || descendant(second, first);
    }

    private static boolean descendant(String path, String ancestor) {
        return path.equals(ancestor) || path.startsWith(ancestor + "/");
    }
}
