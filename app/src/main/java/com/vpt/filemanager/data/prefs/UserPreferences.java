package com.vpt.filemanager.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public final class UserPreferences {
    private static final String PREFS_NAME = "user_preferences";
    private static final String KEY_SHOW_HIDDEN = "show_hidden";

    private final SharedPreferences preferences;

    @Inject
    public UserPreferences(@ApplicationContext Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean showHidden() {
        return preferences.getBoolean(KEY_SHOW_HIDDEN, false);
    }

    public void setShowHidden(boolean value) {
        preferences.edit().putBoolean(KEY_SHOW_HIDDEN, value).apply();
    }
}

