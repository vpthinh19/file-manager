package com.vpt.filemanager.storage.persistence.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "trash")
public final class TrashRecord {
    @PrimaryKey @NonNull public String id = "";
    @NonNull @ColumnInfo(name = "original_path") public String originalPath = "";
    @NonNull @ColumnInfo(name = "stored_path") public String storedPath = "";
    @NonNull @ColumnInfo(name = "display_name") public String displayName = "";
    @ColumnInfo(name = "deleted_at") public long deletedAt;
    @ColumnInfo(name = "size_bytes") public long sizeBytes;
    public boolean directory;
}
