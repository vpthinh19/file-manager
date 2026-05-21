package com.vpt.filemanager.support;

import com.vpt.filemanager.BuildConfig;
import timber.log.Timber;

public final class TimberInit {
    private TimberInit() {
    }

    public static void init() {
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
    }
}

