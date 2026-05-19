package com.vpt.filemanager.ui.browser;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;

import com.vpt.filemanager.R;

/**
 * Routes {@link IconCategory} to its visuals (badge color + white glyph drawable). Single dispatch
 * point so adding a category is one switch arm here + one color pair + one glyph drawable.
 *
 * <p>Folder uses the same dispatch but with the dedicated folder badge color — keeps the rendering
 * loop uniform (every row = colored badge + white glyph).
 */
final class IconMapper {
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
}
