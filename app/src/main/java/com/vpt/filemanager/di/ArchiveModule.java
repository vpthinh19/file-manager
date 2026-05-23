package com.vpt.filemanager.di;

import javax.inject.Singleton;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

import com.vpt.filemanager.node.source.archive.ArchiveMutationBackend;
import com.vpt.filemanager.node.source.archive.ZipArchiveMutationBackend;

/** Selects the physical archive commit implementation behind the virtual archive source. */
@Module
@InstallIn(SingletonComponent.class)
public abstract class ArchiveModule {
    @Binds
    @Singleton
    abstract ArchiveMutationBackend bindArchiveMutationBackend(ZipArchiveMutationBackend backend);
}
