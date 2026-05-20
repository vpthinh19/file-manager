package com.vpt.filemanager.data.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.vpt.filemanager.data.db.dao.BookmarkDao;
import com.vpt.filemanager.data.db.dao.TrashDao;
import com.vpt.filemanager.data.db.entity.BookmarkEntryEntity;
import com.vpt.filemanager.data.db.entity.TrashEntryEntity;

/**
 * Single Room database for the app.
 *
 * <p>Schema history:
 * <ul>
 *   <li>v1 — {@code trash_entries} only (Phase 2C-5c)</li>
 *   <li>v2 — add {@code bookmark_entries} (Phase R-4, migration {@code MIGRATION_1_2})</li>
 * </ul>
 *
 * <p>Schema JSON exports live in {@code app/schemas/} (configured in {@code build.gradle.kts}).
 * Use them to diff between versions when authoring future migrations.
 */
@Database(
        entities = {TrashEntryEntity.class, BookmarkEntryEntity.class},
        version = 2,
        exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public abstract TrashDao trashDao();

    public abstract BookmarkDao bookmarkDao();
}
