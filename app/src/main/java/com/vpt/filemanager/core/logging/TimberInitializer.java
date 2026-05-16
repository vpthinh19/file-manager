package com.vpt.filemanager.core.logging;

import com.vpt.filemanager.BuildConfig;
import timber.log.Timber;

public final class TimberInitializer {
    private TimberInitializer() {
    }

    public static void init() {
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
    }
}

