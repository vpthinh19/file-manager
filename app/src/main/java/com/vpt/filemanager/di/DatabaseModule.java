package com.vpt.filemanager.di;

import android.content.Context;

import androidx.room.Room;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

import com.vpt.filemanager.storage.persistence.AppDatabase;
import com.vpt.filemanager.storage.persistence.dao.BookmarkDao;
import com.vpt.filemanager.storage.persistence.dao.TrashDao;

@Module
@InstallIn(SingletonComponent.class)
public final class DatabaseModule {
    private DatabaseModule() {
    }

    @Provides
    @Singleton
    public static AppDatabase provideDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, "workspace.db").build();
    }

    @Provides public static TrashDao provideTrashDao(AppDatabase database) {
        return database.trashDao();
    }

    @Provides public static BookmarkDao provideBookmarkDao(AppDatabase database) {
        return database.bookmarkDao();
    }
}
