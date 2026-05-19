package com.vpt.filemanager.ui.browser;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.Date;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.util.ByteSize;
import com.vpt.filemanager.domain.model.FileNode;

/**
 * Renders a single {@link FileNode} row.
 *
 * <p>Icon rendering delegated to {@link FileIconView}: folder rows take the folder badge directly,
 * file rows look up an {@link IconCategory} from the file name. Selection state is propagated to
 * the itemView via {@link View#setSelected(boolean)} so {@code bg_file_row} can pick up the right
 * state-list color.
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
            icon.bindCategory(IconCategory.ofFileName(node.name()));
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
