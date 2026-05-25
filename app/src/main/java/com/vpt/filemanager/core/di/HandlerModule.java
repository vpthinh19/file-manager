package com.vpt.filemanager.core.di;

import com.vpt.filemanager.handler.AudioHandler;
import com.vpt.filemanager.handler.Handler;
import com.vpt.filemanager.handler.ImageHandler;
import com.vpt.filemanager.handler.OtherHandler;
import com.vpt.filemanager.handler.TextHandler;
import com.vpt.filemanager.handler.VideoHandler;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import dagger.multibindings.IntoSet;

/**
 * Collects every {@link Handler} into the {@code Set<Handler>} consumed by
 * {@link com.vpt.filemanager.handler.HandlerRegistry}. Adding a new content
 * type means adding one more {@code @Binds @IntoSet} method here.
 */
@Module
@InstallIn(SingletonComponent.class)
public abstract class HandlerModule {

    @Binds
    @IntoSet
    public abstract Handler bindTextHandler(TextHandler text);

    @Binds
    @IntoSet
    public abstract Handler bindImageHandler(ImageHandler image);

    @Binds
    @IntoSet
    public abstract Handler bindAudioHandler(AudioHandler audio);

    @Binds
    @IntoSet
    public abstract Handler bindVideoHandler(VideoHandler video);

    @Binds
    @IntoSet
    public abstract Handler bindOtherHandler(OtherHandler other);
}
