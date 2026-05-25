package com.vpt.filemanager.storage.persistence.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "bookmarks")
public final class BookmarkRecord {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String path = "";
    @NonNull @ColumnInfo(name = "display_name") public String displayName = "";
    @ColumnInfo(name = "added_at") public long addedAt;
    public int position;
}
