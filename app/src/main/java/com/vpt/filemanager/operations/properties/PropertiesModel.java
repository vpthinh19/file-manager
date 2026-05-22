package com.vpt.filemanager.operations.properties;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vpt.filemanager.node.NodePath;

/**
 * UI-neutral data model for the Properties dialog.
 */
public final class PropertiesModel {
    @NonNull public final NodePath path;
    @NonNull public final String name;
    @NonNull public final String parent;
    public final boolean folder;
    public final long sizeBytes;
    public final long modifiedAtMillis;
    @Nullable public final PosixMetadata posixMetadata;

    public PropertiesModel(@NonNull NodePath path,
                           @NonNull String name,
                           @NonNull String parent,
                           boolean folder,
                           long sizeBytes,
                           long modifiedAtMillis,
                           @Nullable PosixMetadata posixMetadata) {
        this.path = path;
        this.name = name;
        this.parent = parent;
        this.folder = folder;
        this.sizeBytes = sizeBytes;
        this.modifiedAtMillis = modifiedAtMillis;
        this.posixMetadata = posixMetadata;
    }

    public static final class PosixMetadata {
        @NonNull public final String permissions;
        @NonNull public final String owner;
        @NonNull public final String group;

        public PosixMetadata(@NonNull String permissions,
                             @NonNull String owner,
                             @NonNull String group) {
            this.permissions = permissions;
            this.owner = owner;
            this.group = group;
        }
    }
}
