package com.vpt.filemanager.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Row representation của một bookmark trong drawer "Bookmark" (Phase R-8 sẽ wire UI). Pattern
 * mirror {@code TrashEntryEntity}: snake_case column, default empty string, Room annotation
 * gói trong data layer (domain stays Android-free).
 *
 * <p>{@code position} cho phép user reorder bookmarks trong tương lai (drag-and-drop). v1 chỉ
 * dùng làm secondary sort sau {@code addedAtMillis}.
 */
@Entity(tableName = "bookmark_entries")
public final class BookmarkEntryEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id = "";

    /** Absolute local path tới folder hoặc file được bookmark. v1 chỉ bookmark folder. */
    @NonNull
    @ColumnInfo(name = "path")
    public String path = "";

    /** Tên hiển thị (default = tên folder; user có thể đổi sau). */
    @NonNull
    @ColumnInfo(name = "display_name")
    public String displayName = "";

    @ColumnInfo(name = "added_at_millis")
    public long addedAtMillis;

    @ColumnInfo(name = "position")
    public int position;
}
