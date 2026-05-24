package com.vpt.filemanager.data.persistence;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.vpt.filemanager.data.persistence.dao.BookmarkDao;
import com.vpt.filemanager.data.persistence.dao.TrashDao;
import com.vpt.filemanager.data.persistence.entity.BookmarkRecord;
import com.vpt.filemanager.data.persistence.entity.TrashRecord;

@Database(entities = {BookmarkRecord.class, TrashRecord.class}, version = 1, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public abstract BookmarkDao bookmarkDao();
    public abstract TrashDao trashDao();
}
