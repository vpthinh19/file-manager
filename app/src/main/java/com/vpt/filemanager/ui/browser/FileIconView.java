package com.vpt.filemanager.ui.browser;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.vpt.filemanager.R;
import com.vpt.filemanager.domain.model.FileCategory;

/**
 * Compound view that renders a file's icon: a colored rounded-square badge with a short text label
 * (e.g. {@code PDF}) for files, or a folder shape for directories.
 *
 * <p>One view, two states (folder vs file), set via {@link #bindFolder()} / {@link #bindFile}.
 * Color tinting goes through {@link FileLabel#categoryColorRes(FileCategory)}, so adding a category
 * requires changes only in {@link FileCategory} + {@link FileLabel} + colors.xml — no edits here.
 */
public final class FileIconView extends FrameLayout {
    private final View badgeBg;
    private final TextView label;
    private final ImageView folder;

    public FileIconView(Context context) {
        this(context, null);
    }

    public FileIconView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FileIconView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.view_file_icon, this, true);
        badgeBg = findViewById(R.id.v_badge_bg);
        label = findViewById(R.id.tv_badge_label);
        folder = findViewById(R.id.iv_folder);
    }

    /** Render as a folder. Tint of the folder vector comes from the bundled drawable. */
    public void bindFolder() {
        badgeBg.setVisibility(GONE);
        label.setVisibility(GONE);
        folder.setVisibility(VISIBLE);
    }

    /** Render as a file with the given category — drives both badge color and label text. */
    public void bindFile(FileCategory category) {
        folder.setVisibility(GONE);
        badgeBg.setVisibility(VISIBLE);
        label.setVisibility(VISIBLE);
        int color = ContextCompat.getColor(getContext(), FileLabel.categoryColorRes(category));
        badgeBg.setBackgroundTintList(ColorStateList.valueOf(color));
        label.setText(category.shortLabel);
    }

    /** Render the parent-directory ".." marker (folder shape, same tint as a regular folder). */
    public void bindParent() {
        bindFolder();
    }
}
