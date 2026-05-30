package com.vpt.filemanager.storage.virtual.archive;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** User-selected settings for a newly created archive. */
public record ArchiveCreateOptions(@NonNull Format format, @NonNull Level level,
                                   @Nullable String password) {
    public ArchiveCreateOptions {
        if (password != null && password.isBlank()) password = null;
        if (password != null && !format.supportsPassword()) {
            throw new IllegalArgumentException("Password protection is only supported for ZIP");
        }
    }

    public enum Format {
        ZIP(".zip", true),
        SEVEN_Z(".7z", false),
        TAR_GZIP(".tar.gz", false);

        private final String extension;
        private final boolean supportsPassword;

        Format(String extension, boolean supportsPassword) {
            this.extension = extension;
            this.supportsPassword = supportsPassword;
        }

        @NonNull
        public String extension() {
            return extension;
        }

        public boolean supportsPassword() {
            return supportsPassword;
        }
    }

    public enum Level {
        STORE(0),
        FAST(3),
        NORMAL(6),
        MAXIMUM(9);

        private final int nativeValue;

        Level(int nativeValue) {
            this.nativeValue = nativeValue;
        }

        public int nativeValue() {
            return nativeValue;
        }
    }
}
