package com.vpt.filemanager.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import com.vpt.filemanager.data.db.entity.TrashEntryEntity;

@Dao
public interface TrashDao {
    /** Observable list — Room re-emits whenever the table changes, so the UI auto-refreshes. */
    @Query("SELECT * FROM trash_entries ORDER BY deleted_at_millis DESC")
    LiveData<List<TrashEntryEntity>> observeAll();

    /** Synchronous snapshot — used by repository ops (restore, empty) that already run on io(). */
    @Query("SELECT * FROM trash_entries ORDER BY deleted_at_millis DESC")
    List<TrashEntryEntity> all();

    @Query("SELECT * FROM trash_entries WHERE id = :id LIMIT 1")
    TrashEntryEntity findById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TrashEntryEntity entity);

    @Query("DELETE FROM trash_entries WHERE id = :id")
    void deleteById(String id);

    @Query("DELETE FROM trash_entries")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM trash_entries")
    int count();
}
