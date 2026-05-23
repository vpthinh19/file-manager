package com.vpt.filemanager.rules.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vpt.filemanager.node.NodePath;

/**
 * Single source of truth for the storage scope the app is allowed to navigate within.
 *
 * <p>Decision (Phase 2C-1a): the user-facing root is restricted to primary external storage
 * ({@code /storage/emulated/0}). The app never navigates above it, mirroring Xiaomi's File Manager
 * scope. System partitions are intentionally hidden — they are noise for an unrooted file manager.
 */
public final class StorageScope {
    public static final String ROOT_PATH = "/storage/emulated/0";

    private StorageScope() {
    }

    @NonNull
    public static NodePath rootPath() {
        return NodePath.STORAGE_ROOT;
    }

    public static boolean isAtRoot(@Nullable NodePath path) {
        return path != null && path.isLocal() && ROOT_PATH.equals(path.path());
    }

    /**
     * @return {@code true} when navigating one level up from {@code path} remains inside the scope.
     * Archive paths always allow going up — the parent ultimately lands back in local scope.
     */
    public static boolean canGoUp(@Nullable NodePath path) {
        if (path == null || path.isRoot()) {
            return false;
        }
        if (path.equals(NodePath.TRASH_ROOT) || path.equals(NodePath.BOOKMARK_ROOT)
                || isAtRoot(path)) {
            return true;
        }
        if (path.isArchive() || path.isSearch()) {
            return true;
        }
        return path.isLocal();
    }

    /**
     * Storage root that owns the given local path. v1 only allows {@link #ROOT_PATH}, so this is
     * effectively a constant; the indirection exists so that when we add SD-card or SAF support
     * (Phase 2D), {@code .AppTrash} placement keeps working without callers having to learn the
     * new storage layout.
     */
    @NonNull
    public static String storageRootFor(@NonNull String localPath) {
        return ROOT_PATH;
    }
}
