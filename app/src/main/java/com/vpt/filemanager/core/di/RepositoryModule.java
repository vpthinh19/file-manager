package com.vpt.filemanager.core.di;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import com.vpt.filemanager.data.repository.FileRepositoryImpl;
import com.vpt.filemanager.data.repository.TrashRepositoryImpl;
import com.vpt.filemanager.domain.repository.FileRepository;
import com.vpt.filemanager.domain.repository.TrashRepository;

/**
 * Hilt bindings for the active repositories. {@code HashRepository} and {@code BookmarkRepository}
 * were removed along with their unused use cases — when those features get wired we'll add
 * concrete implementations + their {@code @Binds} entries together rather than carrying empty
 * scaffolding indefinitely.
 */
@Module
@InstallIn(SingletonComponent.class)
public abstract class RepositoryModule {
    @Binds
    public abstract FileRepository bindFileRepository(FileRepositoryImpl repository);

    @Binds
    public abstract TrashRepository bindTrashRepository(TrashRepositoryImpl repository);
}
