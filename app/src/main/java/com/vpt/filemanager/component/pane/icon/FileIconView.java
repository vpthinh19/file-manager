package com.vpt.filemanager.component.pane.icon;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vpt.filemanager.R;

/**
 * Renders a row's icon as a rounded badge + white glyph; each {@link IconCategory} maps to one
 * badge color + one glyph through {@link IconMapper}. {@link #bindCategory} pulls a cached tint so
 * scroll-time allocations stay flat.
 */
public final class FileIconView extends FrameLayout {
    private final View badgeBg;
    private final ImageView glyph;
    @Nullable
    private IconCategory currentCategory;

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
        // Skip redundant rebinds — selection toggles re-bind unchanged rows.
        if (category == currentCategory) {
            return;
        }
        currentCategory = category;
        badgeBg.setBackgroundTintList(IconMapper.badgeTint(getContext(), category));
        glyph.setImageResource(IconMapper.glyph(category));
    }
}
