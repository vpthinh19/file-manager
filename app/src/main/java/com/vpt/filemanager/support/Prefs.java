package com.vpt.filemanager.support;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

import com.vpt.filemanager.support.SortOrder;

/**
 * Thin typed wrapper over {@link SharedPreferences}. One file, one bag of typed getters/setters —
 * callers never touch raw keys or {@code apply()/commit()}. Add a key by adding one accessor pair.
 */
@Singleton
public final class Prefs {
    private static final String FILE = "fm_prefs";
    private static final String KEY_SORT_ORDER = "sort_order";

    private final SharedPreferences sp;

    @Inject
    public Prefs(@ApplicationContext Context ctx) {
        this.sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    @NonNull
    public SortOrder sortOrder() {
        return SortOrder.safeValueOf(sp.getString(KEY_SORT_ORDER, null));
    }

    public void setSortOrder(@NonNull SortOrder order) {
        sp.edit().putString(KEY_SORT_ORDER, order.name()).apply();
    }
}
