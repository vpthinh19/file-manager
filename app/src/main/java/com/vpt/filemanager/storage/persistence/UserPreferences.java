package com.vpt.filemanager.storage.persistence;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import com.vpt.filemanager.model.SortOption;

/** Persistence of user choices; sort logic remains in the action package. */
@Singleton
public final class UserPreferences {
    private static final String FILE = "file_manager_preferences";
    private static final String SORT_ORDER = "sort_order";
    private static final String DARK_THEME = "dark_theme";
    private final SharedPreferences values;

    @Inject
    public UserPreferences(@ApplicationContext Context context) {
        values = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    @NonNull
    public SortOption sortOption() {
        return SortOption.safeValueOf(values.getString(SORT_ORDER, null));
    }

    public void setSortOption(@NonNull SortOption order) {
        values.edit().putString(SORT_ORDER, order.name()).apply();
    }

    @Nullable
    public Boolean darkThemeOverride() {
        return values.contains(DARK_THEME) ? values.getBoolean(DARK_THEME, false) : null;
    }

    public void setDarkTheme(boolean enabled) {
        values.edit().putBoolean(DARK_THEME, enabled).apply();
    }
}
