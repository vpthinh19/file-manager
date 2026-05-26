package com.vpt.filemanager;

import android.app.Application;

import com.vpt.filemanager.threading.AppExecutors;
import com.vpt.filemanager.handler.backend.document.SyntaxSetup;

import java.io.IOException;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;
import timber.log.Timber;

/** Android process entry point and one-time text editor warm-up. */
@HiltAndroidApp
public final class FileManagerApp extends Application {
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
                Timber.w(error, "Unable to prewarm TextMate setup");
            }
        });
    }
}
