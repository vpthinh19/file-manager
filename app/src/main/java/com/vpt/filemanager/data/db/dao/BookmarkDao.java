package com.vpt.filemanager.data.db.dao;

import java.util.List;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.vpt.filemanager.data.db.entity.BookmarkEntity;

@Dao
public interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY sort_order ASC, created_at ASC")
    List<BookmarkEntity> list();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(BookmarkEntity entity);

    @Query("DELETE FROM bookmarks WHERE path = :path")
    void delete(String path);
}

