package com.vpt.filemanager.core.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vpt.filemanager.domain.model.FilePath;

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
    public static FilePath rootPath() {
        return FilePath.local(ROOT_PATH);
    }

    public static boolean isAtRoot(@Nullable FilePath path) {
        return path != null && path.isLocal() && ROOT_PATH.equals(path.path());
    }

    /**
     * @return {@code true} when navigating one level up from {@code path} remains inside the scope.
     * Archive paths always allow going up — the parent ultimately lands back in local scope.
     */
    public static boolean canGoUp(@Nullable FilePath path) {
        if (path == null) {
            return false;
        }
        if (path.isArchive()) {
            return true;
        }
        return path.isLocal() && !ROOT_PATH.equals(path.path());
    }

    /**
     * Strip the storage root prefix from a local path for user-friendly display.
     * Examples: {@code /storage/emulated/0/Download} → {@code /Download};
     * root itself → {@code /}.
     */
    @NonNull
    public static String displayPath(@NonNull String localPath) {
        if (localPath.equals(ROOT_PATH)) {
            return "/";
        }
        if (localPath.startsWith(ROOT_PATH + "/")) {
            return localPath.substring(ROOT_PATH.length());
        }
        return localPath;
    }
}
