package com.vpt.filemanager;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

import dagger.hilt.android.HiltAndroidApp;
import com.vpt.filemanager.core.logging.TimberInitializer;

@HiltAndroidApp
public class FileManagerApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        TimberInitializer.init();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}

