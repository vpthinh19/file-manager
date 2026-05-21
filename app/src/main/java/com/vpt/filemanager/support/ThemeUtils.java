package com.vpt.filemanager.support;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.util.TypedValue;
import android.view.Window;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Small bridge between Java code and theme attributes. Centralised so the lookup pattern is the
 * same everywhere and easy to unit-test via Robolectric.
 */
public final class ThemeUtils {
    private ThemeUtils() {
    }

    @ColorInt
    public static int color(Context context, @AttrRes int attr) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) {
                return context.getColor(value.resourceId);
            }
            return value.data;
        }
        return 0;
    }

    public static boolean isLightTheme(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode != Configuration.UI_MODE_NIGHT_YES;
    }
}
