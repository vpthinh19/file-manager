package com.vpt.filemanager.core.di;

import com.vpt.filemanager.storage.Storage;
import com.vpt.filemanager.storage.archive.ArchiveStorage;
import com.vpt.filemanager.storage.bookmarks.BookmarkStorage;
import com.vpt.filemanager.storage.local.LocalStorage;
import com.vpt.filemanager.storage.search.SearchStorage;
import com.vpt.filemanager.storage.trash.TrashStorage;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import dagger.multibindings.IntoSet;

/**
 * Collects every {@link Storage} implementation into the {@code Set<Storage>}
 * consumed by {@link com.vpt.filemanager.storage.StorageRegistry}. Adding a new
 * backend means adding one more {@code @Binds @IntoSet} method here; nothing
 * else changes.
 */
@Module
@InstallIn(SingletonComponent.class)
public abstract class StorageModule {

    @Binds
    @IntoSet
    public abstract Storage bindLocalStorage(LocalStorage local);

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
