package com.vpt.filemanager.ui.browser;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.vpt.filemanager.R;
import com.vpt.filemanager.domain.model.FileCategory;

/**
 * Renders a single row's icon in one of three mutually-exclusive modes.
 *
 * <p><b>Folder mode</b>: neutral dark badge + white folder glyph. MT-Manager style — folder
 * blends with theme yet the white shape stays legible.
 *
 * <p><b>Glyph mode</b> (TOP-7 categories): category-colored badge + white type glyph (PDF, image,
 * video, audio, archive, APK). Strong visual recognition for the most-common file kinds.
 *
 * <p><b>Ext-text mode</b>: paper-shape vector tinted to the per-extension brand color
 * (see {@link BrandColors}) with the uppercase extension overlaid. Scales to any extension
 * without needing a custom glyph; ideal for source code, docs, configs.
 *
 * <p>The mode dispatch lives in {@link FileViewHolder}; this view exposes one entry point per
 * mode and stays dumb on purpose.
 */
public final class FileIconView extends FrameLayout {
    private final View badgeBg;
    private final ImageView glyph;
    private final ImageView fileShape;
    private final TextView label;

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
        fileShape = findViewById(R.id.iv_file_shape);
        label = findViewById(R.id.tv_badge_label);
    }

    /** Folder + parent-marker rows. Always uses the neutral folder badge color. */
    public void bindFolder() {
        showBadgeWithGlyph(R.color.badge_folder, R.drawable.ic_folder);
    }

    /** TOP-7 file categories — colored badge + white type glyph. */
    public void bindGlyph(@NonNull FileCategory category) {
        showBadgeWithGlyph(FileLabel.categoryColorRes(category), FileLabel.glyphIconRes(category));
    }

    /** Non-TOP-7 files (TEXT/CODE/DOC/UNKNOWN) — paper shape with brand color + ext label. */
    public void bindExtText(@NonNull String fileName) {
        badgeBg.setVisibility(GONE);
        glyph.setVisibility(GONE);
        fileShape.setVisibility(VISIBLE);
        label.setVisibility(VISIBLE);
        fileShape.setImageResource(R.drawable.ic_file_shape);
        int tint = ContextCompat.getColor(getContext(), BrandColors.colorFor(fileName));
        fileShape.setImageTintList(ColorStateList.valueOf(tint));
        label.setText(BrandColors.label(fileName));
    }

    private void showBadgeWithGlyph(int badgeColorRes, int glyphDrawableRes) {
        fileShape.setVisibility(GONE);
        label.setVisibility(GONE);
        badgeBg.setVisibility(VISIBLE);
        glyph.setVisibility(VISIBLE);
        int bgColor = ContextCompat.getColor(getContext(), badgeColorRes);
        badgeBg.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        glyph.setImageResource(glyphDrawableRes);
    }
}
