package com.vpt.filemanager.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Row representation of one item sitting in the user's trash. Mirrors {@code TrashEntry} in the
 * domain layer — kept as a separate Room-annotated class so the domain model stays storage-agnostic
 * (no Room annotations leak into pure Java).
 */
@Entity(tableName = "trash_entries")
public final class TrashEntryEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id = "";

    @NonNull
    @ColumnInfo(name = "original_path")
    public String originalPath = "";

    @NonNull
    @ColumnInfo(name = "display_name")
    public String displayName = "";

    @NonNull
    @ColumnInfo(name = "trash_path")
    public String trashPath = "";

    @ColumnInfo(name = "deleted_at_millis")
    public long deletedAtMillis;

    @ColumnInfo(name = "size_bytes")
    public long sizeBytes;

    @ColumnInfo(name = "directory")
    public boolean directory;
}
