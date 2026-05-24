package com.vpt.filemanager.browser.action.properties;

import androidx.annotation.Nullable;

public final class PropertiesModel {
    public final String path;
    public final String name;
    public final String parent;
    public final boolean folder;
    public final long sizeBytes;
    public final long modifiedAtMillis;
    @Nullable public final PosixMetadata posixMetadata;

    public PropertiesModel(String path, String name, String parent, boolean folder, long sizeBytes,
                           long modifiedAtMillis, @Nullable PosixMetadata posixMetadata) {
        this.path = path;
        this.name = name;
        this.parent = parent;
        this.folder = folder;
        this.sizeBytes = sizeBytes;
        this.modifiedAtMillis = modifiedAtMillis;
        this.posixMetadata = posixMetadata;
    }

    public record PosixMetadata(String permissions, String owner, String group) {
    }
}
