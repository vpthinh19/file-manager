package com.vpt.filemanager.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import com.vpt.filemanager.data.db.entity.BookmarkEntryEntity;

/**
 * Room DAO cho bookmark. Pattern mirror {@code TrashDao}: observe() cho UI reactive, all() sync
 * cho Ops chạy trên io. findByPath cho duplicate check (idempotent add).
 */
@Dao
public interface BookmarkDao {
    @Query("SELECT * FROM bookmark_entries ORDER BY position ASC, added_at_millis DESC")
    LiveData<List<BookmarkEntryEntity>> observeAll();

    @Query("SELECT * FROM bookmark_entries ORDER BY position ASC, added_at_millis DESC")
    List<BookmarkEntryEntity> all();

    @Query("SELECT * FROM bookmark_entries WHERE path = :path LIMIT 1")
    BookmarkEntryEntity findByPath(String path);

    /** {@code null} nếu table rỗng. Caller dùng {@code maxPosition() == null ? 0 : max + 1}. */
    @Query("SELECT MAX(position) FROM bookmark_entries")
    Integer maxPosition();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BookmarkEntryEntity entity);

    @Query("DELETE FROM bookmark_entries WHERE id = :id")
    void deleteById(String id);

    @Query("DELETE FROM bookmark_entries WHERE path = :path")
    void deleteByPath(String path);

    @Query("SELECT COUNT(*) FROM bookmark_entries")
    int count();
}
