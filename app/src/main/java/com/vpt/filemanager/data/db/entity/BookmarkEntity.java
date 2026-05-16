package com.vpt.filemanager.data.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "bookmarks")
public final class BookmarkEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "display_name")
    public String displayName;

    public String path;

    @ColumnInfo(name = "icon_key")
    public String iconKey;

    @ColumnInfo(name = "sort_order")
    public int sortOrder;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}

