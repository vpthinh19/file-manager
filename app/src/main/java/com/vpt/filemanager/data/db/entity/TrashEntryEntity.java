package com.vpt.filemanager.data.db.entity;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "trash_entries")
public final class TrashEntryEntity {
    @PrimaryKey
    @NonNull
    public String id;

    @ColumnInfo(name = "original_path")
    public String originalPath;

    @ColumnInfo(name = "trash_path")
    public String trashPath;

    @ColumnInfo(name = "storage_root")
    public String storageRoot;

    @ColumnInfo(name = "display_name")
    public String displayName;

    @ColumnInfo(name = "size_bytes")
    public long sizeBytes;

    @ColumnInfo(name = "is_directory")
    public boolean directory;

    @ColumnInfo(name = "deleted_at")
    public long deletedAt;

    @Nullable
    @ColumnInfo(name = "expires_at")
    public Long expiresAt;
}
