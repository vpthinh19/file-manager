package com.vpt.filemanager.di;

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

/**
 * Collects every {@link Storage} implementation into the {@code Set<Storage>}
 * consumed by {@link StorageRegistry}. Adding a new
 * backend means adding one more {@code @Binds @IntoSet} method here; nothing
 * else changes.
 */
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
