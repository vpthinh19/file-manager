package com.vpt.filemanager.app;

import android.app.Application;

import com.vpt.filemanager.BuildConfig;
import com.vpt.filemanager.threading.AppExecutors;
import com.vpt.filemanager.ui.editor.SyntaxSetup;

import java.io.IOException;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;
import timber.log.Timber;

/**
 * Application root. We intentionally do NOT call
 * {@code DynamicColors.applyToActivitiesIfAvailable} — Material-You wallpaper-derived colors
 * would override our carefully-tuned MT-style palette (the wrong pink/cream surfaces seen on
 * 2026-05-18 dark-mode screenshots). Our static tokens in {@code colors.xml} / {@code values-night}
 * are the single source of truth.
 */
@HiltAndroidApp
public class FileManagerApp extends Application {
    @Inject
    AppExecutors executors;

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
        executors.computation().execute(() -> {
            try {
                SyntaxSetup.prewarm(this);
            } catch (IOException error) {
                Timber.w(error, "Unable to prewarm TextMate infrastructure");
            }
        });
    }
}
