package com.vpt.filemanager.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "recent_paths", indices = @Index(value = "last_visited_at"))
public final class RecentPathEntity {
    @PrimaryKey
    @NonNull
    public String path;

    @ColumnInfo(name = "last_visited_at")
    public long lastVisitedAt;

    @ColumnInfo(name = "visit_count")
    public int visitCount;
}
