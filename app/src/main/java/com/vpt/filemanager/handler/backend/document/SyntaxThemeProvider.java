package com.vpt.filemanager.handler.backend.document;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

/** Selects the bundled TextMate theme matching the active Android night mode. */
public final class SyntaxThemeProvider {
    private static final String ASSET_DARK = "editor/textmate/themes/darcula.json";
    private static final String ASSET_LIGHT = "editor/textmate/themes/quietlight.json";
    private static final String NAME_DARK = "darcula";
    private static final String NAME_LIGHT = "quietlight";

    private SyntaxThemeProvider() {
    }

    @NonNull
    public static String assetPath(@NonNull Context context) {
        return isNight(context) ? ASSET_DARK : ASSET_LIGHT;
    }

    @NonNull
    public static String themeName(@NonNull Context context) {
        return isNight(context) ? NAME_DARK : NAME_LIGHT;
    }

    private static boolean isNight(@NonNull Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }
}
