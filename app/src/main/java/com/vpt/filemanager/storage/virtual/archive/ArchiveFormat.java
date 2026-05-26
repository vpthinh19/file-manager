package com.vpt.filemanager.storage.virtual.archive;

import androidx.annotation.NonNull;

import java.util.Locale;

/** Write capabilities for containers accepted by the archive backend. */
public final class ArchiveFormat {
    private ArchiveFormat() {
    }

    public static boolean isWritable(@NonNull String container) {
        String path = container.toLowerCase(Locale.ROOT);
        return endsWith(path, ".zip", ".jar", ".apk", ".war", ".aar", ".7z", ".tar", ".tar.gz", ".tgz",
                ".tar.bz2", ".tbz", ".tbz2", ".tar.xz", ".txz", ".tar.zst", ".tzst",
                ".cpio", ".xar", ".ar");
    }

    private static boolean endsWith(String path, String... extensions) {
        for (String extension : extensions) {
            if (path.endsWith(extension)) return true;
        }
        return false;
    }
}
