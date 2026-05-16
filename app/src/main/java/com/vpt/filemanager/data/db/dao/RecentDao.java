package com.vpt.filemanager.data.db.dao;

import java.util.List;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.vpt.filemanager.data.db.entity.RecentPathEntity;

@Dao
public interface RecentDao {
    @Query("SELECT * FROM recent_paths ORDER BY last_visited_at DESC LIMIT :limit")
    List<RecentPathEntity> list(int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(RecentPathEntity entity);
}

