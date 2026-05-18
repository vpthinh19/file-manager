package com.vpt.filemanager.ui.browser;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;

import com.vpt.filemanager.R;
import com.vpt.filemanager.domain.model.FileCategory;

/**
 * UI-layer mapper for {@link FileCategory} → drawable/color resources. Categorisation lives in
 * {@link FileCategory#ofExtension(String)}; this class only routes a category to its visuals.
 *
 * <p>Two rendering modes for files:
 *   <ul>
 *     <li><b>Glyph mode</b> ({@link #usesGlyph}): TOP-7 categories (PDF, IMAGE, VIDEO, AUDIO,
 *         ARCHIVE, APK, plus the CODE generic) show a white glyph on a colored badge.</li>
 *     <li><b>Ext-text mode</b>: everything else (TEXT, DOC, UNKNOWN) renders as a paper-shape
 *         file icon tinted to the per-extension brand color (see {@link BrandColors}) with the
 *         uppercase extension as an overlay label.</li>
 *   </ul>
 *
 * <p>Folder is a third mode, handled directly by {@code FileIconView#bindFolder} — it always uses
 * the neutral folder badge color and the folder glyph.
 */
final class FileLabel {
    private FileLabel() {
    }

    /** @return true when this category should be rendered as colored badge + white glyph. */
    static boolean usesGlyph(FileCategory category) {
        switch (category) {
            case PDF:
            case IMAGE:
            case VIDEO:
            case AUDIO:
            case ARCHIVE:
            case APK:
                return true;
            default:
                return false;
        }
    }

    /** White glyph drawable for the given category — call only when {@link #usesGlyph} is true. */
    @DrawableRes
    static int glyphIconRes(FileCategory category) {
        switch (category) {
            case PDF: return R.drawable.ic_glyph_pdf;
            case IMAGE: return R.drawable.ic_glyph_image;
            case VIDEO: return R.drawable.ic_glyph_video;
            case AUDIO: return R.drawable.ic_glyph_audio;
            case ARCHIVE: return R.drawable.ic_glyph_archive;
            case APK: return R.drawable.ic_glyph_apk;
            default:
                throw new IllegalArgumentException("No glyph for " + category);
        }
    }

    /** Badge background color for glyph-mode files (folder uses {@code badge_folder} directly). */
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
