package com.vpt.filemanager.app.di;

import com.vpt.filemanager.storage.virtual.Storage;
import com.vpt.filemanager.storage.virtual.StorageRegistry;
import com.vpt.filemanager.storage.virtual.archive.ArchiveStorage;
import com.vpt.filemanager.storage.virtual.bookmarks.BookmarkStorage;
import com.vpt.filemanager.storage.virtual.device.DeviceStorage;
import com.vpt.filemanager.storage.virtual.search.SearchStorage;
import com.vpt.filemanager.storage.virtual.trash.TrashStorage;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import dagger.multibindings.IntoSet;

/** Binds every {@link Storage} backend into the set consumed by {@link StorageRegistry}. */
@Module
@InstallIn(SingletonComponent.class)
public abstract class StorageModule {

    @Binds
    @IntoSet
    public abstract Storage bindDeviceStorage(DeviceStorage device);

    @Binds
    @IntoSet
    public abstract Storage bindArchiveStorage(ArchiveStorage archive);

    @Binds
    @IntoSet
    public abstract Storage bindTrashStorage(TrashStorage trash);

    @Binds
    @IntoSet
    public abstract Storage bindBookmarkStorage(BookmarkStorage bookmarks);

    @Binds
    @IntoSet
    public abstract Storage bindSearchStorage(SearchStorage search);
}
