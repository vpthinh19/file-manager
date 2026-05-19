package com.vpt.filemanager.data.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.vpt.filemanager.data.db.dao.TrashDao;
import com.vpt.filemanager.data.db.entity.TrashEntryEntity;

/**
 * Single Room database for the app. Version 1 ships with one table ({@code trash_entries}).
 *
 * <p>Schema files are exported under {@code app/schemas/} (configured in {@code build.gradle.kts})
 * so migrations can be authored against a pinned baseline once we move past v1.
 */
@Database(
        entities = {TrashEntryEntity.class},
        version = 1,
        exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public abstract TrashDao trashDao();
}
