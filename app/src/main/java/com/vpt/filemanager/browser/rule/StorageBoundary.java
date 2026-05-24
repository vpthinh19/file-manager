package com.vpt.filemanager.browser.rule;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vpt.filemanager.browser.item.Path;

/** Makes the public Storage root the highest reachable storage path. */
public final class StorageBoundary {
    public static final String ROOT_PATH = "/storage/emulated/0";

    private StorageBoundary() {
    }

    @NonNull
    public static Path root() {
        return Path.storage(ROOT_PATH);
    }

    public static boolean canNavigateUp(@Nullable Path path) {
        return path != null && path.isStorage()
                && !ROOT_PATH.equals(path.directory());
    }
}
