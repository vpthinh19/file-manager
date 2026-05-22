package com.vpt.filemanager.browser.icon;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import java.util.EnumMap;

import com.vpt.filemanager.R;

/**
 * Routes {@link IconCategory} to its visuals (badge color + white glyph drawable). Single dispatch
 * point so adding a category is one switch arm here + one color pair + one glyph drawable.
 *
 * <p>Folder uses the same dispatch but with the dedicated folder badge color — keeps the rendering
 * loop uniform (every row = colored badge + white glyph).
 *
 * <p>{@link #badgeTint(Context, IconCategory)} memoizes {@link ColorStateList} per category so the
 * RecyclerView binding hot path (~30 rows × 60 fps) does NOT allocate a fresh CSL per row. The
 * cache is invalidated when the system's day/night mode flips (theme swap returns different
 * resolved colors).
 */
final class IconMapper {
    private static final EnumMap<IconCategory, ColorStateList> TINT_CACHE =
            new EnumMap<>(IconCategory.class);
    private static int cachedNightMode = Integer.MIN_VALUE;

    private IconMapper() {
    }

    @ColorRes
    static int badgeColor(IconCategory category) {
        switch (category) {
            case FOLDER: return R.color.icon_folder;
            case DOCUMENT: return R.color.icon_document;
            case SHEET: return R.color.icon_sheet;
            case SLIDE: return R.color.icon_slide;
            case PDF: return R.color.icon_pdf;
            case ARCHIVE: return R.color.icon_archive;
            case IMAGE: return R.color.icon_image;
            case VIDEO: return R.color.icon_video;
            case AUDIO: return R.color.icon_audio;
            case APK: return R.color.icon_apk;
            case CODE: return R.color.icon_code;
            case UNKNOWN: default: return R.color.icon_unknown;
        }
    }

    @DrawableRes
    static int glyph(IconCategory category) {
        switch (category) {
            case FOLDER: return R.drawable.ic_folder;
            case DOCUMENT: return R.drawable.ic_glyph_document;
            case SHEET: return R.drawable.ic_glyph_sheet;
            case SLIDE: return R.drawable.ic_glyph_slide;
            case PDF: return R.drawable.ic_glyph_pdf;
            case ARCHIVE: return R.drawable.ic_glyph_archive;
            case IMAGE: return R.drawable.ic_glyph_image;
            case VIDEO: return R.drawable.ic_glyph_video;
            case AUDIO: return R.drawable.ic_glyph_audio;
            case APK: return R.drawable.ic_glyph_apk;
            case CODE: return R.drawable.ic_glyph_code;
            case UNKNOWN: default: return R.drawable.ic_glyph_unknown;
        }
    }

    /**
     * Lazy-cached {@link ColorStateList} for the badge tint. Survives across RV bind calls; only
     * rebuilds when day/night mode changes (so colors stay live across theme swaps).
     */
    static ColorStateList badgeTint(Context context, IconCategory category) {
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        if (nightMode != cachedNightMode) {
            TINT_CACHE.clear();
            cachedNightMode = nightMode;
        }
        ColorStateList tint = TINT_CACHE.get(category);
        if (tint == null) {
            tint = ColorStateList.valueOf(
                    ContextCompat.getColor(context, badgeColor(category)));
            TINT_CACHE.put(category, tint);
        }
        return tint;
    }
}
