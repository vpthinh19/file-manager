package com.vpt.filemanager.data.archive;

import androidx.annotation.NonNull;

import java.util.Locale;

/** Format capabilities exposed to rules and archive commands. */
public final class ArchiveFormat {
    private ArchiveFormat() {
    }

    public static boolean isWritable(@NonNull String container) {
        String path = container.toLowerCase(Locale.ROOT);
        return endsWith(path, ".zip", ".jar", ".apk", ".war", ".aar", ".7z", ".tar", ".tar.gz", ".tgz",
                ".tar.bz2", ".tbz", ".tbz2", ".tar.xz", ".txz", ".tar.zst", ".tzst",
                ".cpio", ".xar", ".ar");
    }

    public static boolean isContainer(@NonNull String name) {
        String path = name.toLowerCase(Locale.ROOT);
        return endsWith(path, ".zip", ".jar", ".apk", ".war", ".aar", ".7z", ".rar", ".rar5", ".tar",
                ".tar.gz", ".tgz", ".tar.bz2", ".tbz", ".tbz2", ".tar.xz", ".txz",
                ".tar.zst", ".tzst", ".gz", ".bz2", ".xz", ".zst", ".cpio", ".xar",
                ".ar", ".cab", ".iso", ".lha", ".lzh", ".warc");
    }

    private static boolean endsWith(String path, String... extensions) {
        for (String extension : extensions) {
            if (path.endsWith(extension)) return true;
        }
        return false;
    }
}
