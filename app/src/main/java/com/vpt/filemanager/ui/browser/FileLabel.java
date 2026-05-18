package com.vpt.filemanager.ui.browser;

import androidx.annotation.ColorRes;

import com.vpt.filemanager.R;
import com.vpt.filemanager.domain.model.FileCategory;

/**
 * UI-layer mapper from {@link FileCategory} to color resources. Categorisation lives in
 * {@link FileCategory#ofExtension(String)}; this class only routes a category to its badge color.
 *
 * <p>Folder color is baked into {@code ic_folder.xml} via {@code android:tint="@color/badge_folder"}
 * so it's not exposed here.
 */
final class FileLabel {
    private FileLabel() {
    }

    @ColorRes
    static int categoryColorRes(FileCategory category) {
        switch (category) {
            case TEXT: return R.color.badge_text;
            case CODE: return R.color.badge_code;
            case IMAGE: return R.color.badge_image;
            case VIDEO: return R.color.badge_video;
            case AUDIO: return R.color.badge_audio;
            case ARCHIVE: return R.color.badge_archive;
            case PDF: return R.color.badge_pdf;
            case DOC: return R.color.badge_doc;
            case APK: return R.color.badge_apk;
            case UNKNOWN: default: return R.color.badge_unknown;
        }
    }
}
