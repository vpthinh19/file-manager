package com.vpt.filemanager.core.di;

import android.content.Context;

import androidx.room.Room;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

import com.vpt.filemanager.data.db.AppDatabase;
import com.vpt.filemanager.data.db.dao.TrashDao;

@Module
@InstallIn(SingletonComponent.class)
public final class DatabaseModule {
    private static final String DB_NAME = "filemanager.db";

    private DatabaseModule() {
    }

    @Provides
    @Singleton
    public static AppDatabase provideDatabase(@ApplicationContext Context ctx) {
        return Room.databaseBuilder(ctx, AppDatabase.class, DB_NAME)
                // No destructive migrations: when we bump version we'll author a migration. v1 has
                // no prior schema, so a missing migration would be a programmer error not a runtime
                // one, and we want Room to fail loud rather than silently wipe user data.
                .build();
    }

    @Provides
    public static TrashDao provideTrashDao(AppDatabase db) {
        return db.trashDao();
    }
}
