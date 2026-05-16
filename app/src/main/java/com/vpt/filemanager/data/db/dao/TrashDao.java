package com.vpt.filemanager.data.db.dao;

import java.util.List;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.vpt.filemanager.data.db.entity.TrashEntryEntity;

@Dao
public interface TrashDao {
    @Query("SELECT * FROM trash_entries ORDER BY deleted_at DESC")
    List<TrashEntryEntity> list();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(TrashEntryEntity entity);

    @Query("DELETE FROM trash_entries WHERE id = :id")
    void delete(String id);
}

