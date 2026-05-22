package com.vpt.filemanager.data.db;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room migrations cho {@link AppDatabase}. Tách thành class riêng để DatabaseModule nhỏ gọn +
 * dễ test migration logic độc lập.
 *
 * <p>Convention: mỗi migration là 1 {@code public static final Migration MIGRATION_X_Y}. Schema
 * JSON export ở {@code app/schemas/} (configured trong build.gradle.kts) — diff schema giữa các
 * version để verify migration SQL khớp.
 */
public final class AppDatabaseMigrations {
    private AppDatabaseMigrations() {
    }

    /**
     * v1 → v2: add {@code bookmark_entries} table cho Phase R-4 BookmarkStore. Schema khớp với
     * {@link com.vpt.filemanager.data.db.entity.BookmarkEntryEntity}.
     */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `bookmark_entries` ("
                    + "`id` TEXT NOT NULL, "
                    + "`path` TEXT NOT NULL, "
                    + "`display_name` TEXT NOT NULL, "
                    + "`added_at_millis` INTEGER NOT NULL, "
                    + "`position` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`id`))");
        }
    };
}
