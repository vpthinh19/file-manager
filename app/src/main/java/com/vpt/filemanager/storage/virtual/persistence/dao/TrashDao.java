package com.vpt.filemanager.storage.virtual.persistence.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import com.vpt.filemanager.storage.virtual.persistence.entity.TrashRecord;

@Dao
public interface TrashDao {
    @Query("SELECT * FROM trash ORDER BY deleted_at DESC")
    List<TrashRecord> all();

    @Query("SELECT * FROM trash WHERE id = :id LIMIT 1")
    TrashRecord findById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TrashRecord record);

    @Query("DELETE FROM trash WHERE id = :id")
    void deleteById(String id);

    @Query("DELETE FROM trash")
    void deleteAll();
}
