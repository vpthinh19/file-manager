package com.vpt.filemanager.ui.browser;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.Date;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.util.ByteSize;
import com.vpt.filemanager.domain.model.FileCategory;
import com.vpt.filemanager.domain.model.FileNode;

/**
 * Renders a single {@link FileNode} row.
 *
 * <p>Icon rendering delegated to {@link FileIconView}; the holder only decides whether the node is
 * a folder, a parent marker, or a file (and in the file case which category). Selection state is
 * propagated to the itemView via {@link View#setSelected(boolean)}.
 */
public final class FileViewHolder extends RecyclerView.ViewHolder {
    private final FileIconView icon;
    private final TextView name;
    private final TextView meta;

    public FileViewHolder(@NonNull View itemView) {
        super(itemView);
        icon = itemView.findViewById(R.id.file_icon);
        name = itemView.findViewById(R.id.tv_name);
        meta = itemView.findViewById(R.id.tv_meta);
    }

    public void bind(FileNode node, boolean selected) {
        if (node instanceof ParentFileNode || node.isDirectory()) {
            icon.bindFolder();
        } else {
            FileCategory category = FileCategory.ofExtension(node.name());
            if (FileLabel.usesGlyph(category)) {
                icon.bindGlyph(category);
            } else {
                icon.bindExtText(node.name());
            }
        }
        name.setText(node.name());
        meta.setText(formatMeta(node));
        itemView.setSelected(selected);
    }

    private static String formatMeta(FileNode node) {
        String size = node.isDirectory() ? "Folder" : ByteSize.format(node.sizeBytes());
        String date = node.lastModifiedMillis() > 0
                ? DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(node.lastModifiedMillis()))
                : "Parent";
        return size + " · " + date;
    }
}
