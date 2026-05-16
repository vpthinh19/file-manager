package com.vpt.filemanager.core.storage;

import com.vpt.filemanager.domain.model.FilePath;

public final class StorageRoot {
    public enum Kind { INTERNAL, SD_CARD, USB, QUICK_LINK }

    private final String displayName;
    private final FilePath path;
    private final Kind kind;

    public StorageRoot(String displayName, FilePath path, Kind kind) {
        this.displayName = displayName;
        this.path = path;
        this.kind = kind;
    }

    public String displayName() {
        return displayName;
    }

    public FilePath path() {
        return path;
    }

    public Kind kind() {
        return kind;
    }
}
