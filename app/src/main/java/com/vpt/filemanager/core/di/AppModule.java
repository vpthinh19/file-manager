package com.vpt.filemanager.core.di;

import javax.inject.Singleton;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

import com.vpt.filemanager.browser.action.properties.AndroidPropertiesMetadataReader;
import com.vpt.filemanager.browser.action.properties.PropertiesMetadataReader;

@Module
@InstallIn(SingletonComponent.class)
public abstract class AppModule {
    @Binds
    @Singleton
    abstract PropertiesMetadataReader bindPropertiesMetadataReader(
            AndroidPropertiesMetadataReader reader);
}
