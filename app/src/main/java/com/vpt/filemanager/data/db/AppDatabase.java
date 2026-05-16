package com.vpt.filemanager.data.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.vpt.filemanager.data.db.dao.BookmarkDao;
import com.vpt.filemanager.data.db.dao.RecentDao;
import com.vpt.filemanager.data.db.dao.TrashDao;
import com.vpt.filemanager.data.db.entity.BookmarkEntity;
import com.vpt.filemanager.data.db.entity.RecentPathEntity;
import com.vpt.filemanager.data.db.entity.TrashEntryEntity;

@Database(
        version = 1,
        entities = {
                BookmarkEntity.class,
                TrashEntryEntity.class,
                RecentPathEntity.class
        },
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {
    public abstract BookmarkDao bookmarkDao();

    public abstract TrashDao trashDao();

    public abstract RecentDao recentDao();
}

