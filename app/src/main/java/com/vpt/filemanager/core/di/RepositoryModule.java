package com.vpt.filemanager.core.di;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import com.vpt.filemanager.data.repository.BookmarkRepositoryImpl;
import com.vpt.filemanager.data.repository.FileRepositoryImpl;
import com.vpt.filemanager.data.repository.HashRepositoryImpl;
import com.vpt.filemanager.data.repository.TrashRepositoryImpl;
import com.vpt.filemanager.domain.repository.BookmarkRepository;
import com.vpt.filemanager.domain.repository.FileRepository;
import com.vpt.filemanager.domain.repository.HashRepository;
import com.vpt.filemanager.domain.repository.TrashRepository;

@Module
@InstallIn(SingletonComponent.class)
public abstract class RepositoryModule {
    @Binds
    public abstract FileRepository bindFileRepository(FileRepositoryImpl repository);

    @Binds
    public abstract HashRepository bindHashRepository(HashRepositoryImpl repository);

    @Binds
    public abstract TrashRepository bindTrashRepository(TrashRepositoryImpl repository);

    @Binds
    public abstract BookmarkRepository bindBookmarkRepository(BookmarkRepositoryImpl repository);
}

