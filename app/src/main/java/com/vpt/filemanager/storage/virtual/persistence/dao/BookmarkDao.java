package com.vpt.filemanager.storage.virtual.persistence.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import com.vpt.filemanager.storage.virtual.persistence.entity.BookmarkRecord;

@Dao
public interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY position ASC, added_at DESC")
    List<BookmarkRecord> all();

    @Query("SELECT * FROM bookmarks WHERE path = :path LIMIT 1")
    BookmarkRecord findByPath(String path);

    @Query("SELECT MAX(position) FROM bookmarks")
    Integer maxPosition();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BookmarkRecord record);

    @Query("DELETE FROM bookmarks WHERE path = :path")
    void deleteByPath(String path);
}
