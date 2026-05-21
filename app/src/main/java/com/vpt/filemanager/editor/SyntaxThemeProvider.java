package com.vpt.filemanager.editor;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

/**
 * Resolve TextMate theme JSON path theo system night mode hiện tại. Phase R-9.
 *
 * <p>Auto follow {@code Configuration.UI_MODE_NIGHT_*}:
 * <ul>
 *   <li>Night ON → {@code darcula.json}</li>
 *   <li>Night OFF / undefined → {@code quietlight.json}</li>
 * </ul>
 *
 * <p>Khác với {@code ThemeUtils.color}: theme này áp lên syntax tokens (keyword/string/comment),
 * còn ThemeUtils là chrome (status bar, surface). 2 layer độc lập — TextMate theme đến từ JSON
 * bundle assets, không tham gia Material You.
 */
public final class SyntaxThemeProvider {
    private static final String ASSET_DARK = "editor/textmate/themes/darcula.json";
    private static final String ASSET_LIGHT = "editor/textmate/themes/quietlight.json";
    private static final String NAME_DARK = "darcula";
    private static final String NAME_LIGHT = "quietlight";

    private SyntaxThemeProvider() {
    }

    /** Asset path tương đối từ {@code assets/} root. Dùng để feed {@code AssetsFileResolver}. */
    @NonNull
    public static String assetPath(@NonNull Context ctx) {
        return isNight(ctx) ? ASSET_DARK : ASSET_LIGHT;
    }

    /** Tên theme dùng để register vào {@code ThemeRegistry}. Phải unique trong process. */
    @NonNull
    public static String themeName(@NonNull Context ctx) {
        return isNight(ctx) ? NAME_DARK : NAME_LIGHT;
    }

    private static boolean isNight(@NonNull Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }
}
