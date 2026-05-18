package com.vpt.filemanager.ui.browser;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;

import com.vpt.filemanager.R;
import com.vpt.filemanager.domain.model.FileCategory;

/**
 * UI-layer mapper from {@link FileCategory} to drawable / color resources. Pure dispatch — the
 * categorisation logic lives in {@link FileCategory#ofExtension(String)}.
 *
 * <p>Kept package-private to discourage misuse outside the browser package.
 */
final class FileLabel {
    private FileLabel() {
    }

    /** @return short uppercase label (e.g. {@code "PDF"}) shown on the row icon badge. */
    static String shortLabel(String name) {
        return FileCategory.ofExtension(name).shortLabel;
    }

    /** @return shape drawable for the file icon. Folders use {@link #folderIconRes()}. */
    @DrawableRes
    static int fileIconRes(FileCategory category) {
        if (category == FileCategory.UNKNOWN) {
            return R.drawable.ic_file_unknown;
        }
        return R.drawable.ic_file;
    }

    @DrawableRes
    static int folderIconRes() {
        return R.drawable.ic_folder;
    }

    /** @return badge tint color (file icon background + folder fill). */
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

    @ColorRes
    static int folderColorRes() {
        return R.color.badge_folder;
    }
}
