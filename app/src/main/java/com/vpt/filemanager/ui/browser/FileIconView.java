package com.vpt.filemanager.ui.browser;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.vpt.filemanager.R;

/**
 * Renders a row's icon as a rounded badge + white glyph. Every row uses the same visual language:
 * folder, document, sheet, slide, pdf, archive, image, video, audio, apk, code, unknown — all 12
 * map to one badge color + one white glyph drawable through {@link IconMapper}.
 *
 * <p>Previous Option C (per-extension ext-text overlay) removed: long extensions like DOCX/PROP
 * overflowed the badge at large font scales, and per-brand colors fragmented the rail with too many
 * distinct tints. Option D collapses to one badge style with category-level color hierarchy.
 */
public final class FileIconView extends FrameLayout {
    private final View badgeBg;
    private final ImageView glyph;

    public FileIconView(@NonNull Context context) {
        this(context, null);
    }

    public FileIconView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FileIconView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.view_file_icon, this, true);
        badgeBg = findViewById(R.id.v_badge_bg);
        glyph = findViewById(R.id.iv_glyph);
    }

    /** Folder + parent-marker rows. */
    public void bindFolder() {
        bindCategory(IconCategory.FOLDER);
    }

    /** Any file row — derive {@link IconCategory} via {@link IconCategory#ofFileName(String)}. */
    public void bindCategory(@NonNull IconCategory category) {
        int bgColor = ContextCompat.getColor(getContext(), IconMapper.badgeColor(category));
        badgeBg.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        glyph.setImageResource(IconMapper.glyph(category));
    }
}
