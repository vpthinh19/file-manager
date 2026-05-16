package com.vpt.filemanager.core.di;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import dagger.multibindings.IntoSet;
import com.vpt.filemanager.data.fs.FileSystemProvider;
import com.vpt.filemanager.data.fs.archive.ArchiveFileSystemProvider;
import com.vpt.filemanager.data.fs.local.LocalFileSystemProvider;

@Module
@InstallIn(SingletonComponent.class)
public abstract class ProviderModule {
    @Binds
    @IntoSet
    public abstract FileSystemProvider bindLocalProvider(LocalFileSystemProvider provider);

    @Binds
    @IntoSet
    public abstract FileSystemProvider bindArchiveProvider(ArchiveFileSystemProvider provider);
}

