package com.vpt.filemanager;

import android.app.Application;

import dagger.hilt.android.HiltAndroidApp;
import com.vpt.filemanager.core.logging.TimberInitializer;

/**
 * Application root. We intentionally do NOT call
 * {@code DynamicColors.applyToActivitiesIfAvailable} — Material-You wallpaper-derived colors
 * would override our carefully-tuned MT-style palette (the wrong pink/cream surfaces seen on
 * 2026-05-18 dark-mode screenshots). Our static tokens in {@code colors.xml} / {@code values-night}
 * are the single source of truth.
 */
@HiltAndroidApp
public class FileManagerApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        TimberInitializer.init();
    }
}

