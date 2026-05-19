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
 *
 * <p>Perf: click listeners are attached ONCE in the constructor (they read the latest bound node
 * via {@link #currentNode}) — previously {@code onBindViewHolder} allocated a fresh lambda per
 * bind, churning the GC on every scroll. {@link #DATE_FMT} is a class-level static for the same
 * reason: {@code DateFormat.getDateTimeInstance(...)} internally walks Locale / pattern data and
 * allocates a Calendar — far too heavy for the bind hot path.
 */
public final class FileViewHolder extends RecyclerView.ViewHolder {
    /**
     * Shared formatter. {@link DateFormat} is NOT thread-safe, but RV binding is always invoked on
     * the main thread, so a single static instance is safe and avoids the per-bind allocation.
     */
    private static final DateFormat DATE_FMT =
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

    private final FileIconView icon;
    private final TextView name;
    private final TextView meta;
    private final Date dateBuffer = new Date();
    private FileNode currentNode;

    public FileViewHolder(@NonNull View itemView, @NonNull FileListAdapter.Listener listener) {
        super(itemView);
        icon = itemView.findViewById(R.id.file_icon);
        name = itemView.findViewById(R.id.tv_name);
        meta = itemView.findViewById(R.id.tv_meta);
        itemView.setOnClickListener(v -> {
            if (currentNode != null) {
                listener.onFileClicked(currentNode);
            }
        });
        itemView.setOnLongClickListener(v -> {
            if (currentNode != null) {
                listener.onFileLongClicked(currentNode);
                return true;
            }
            return false;
        });
    }

    public void bind(FileNode node, boolean selected) {
        currentNode = node;
        if (node instanceof ParentFileNode || node.isDirectory()) {
            icon.bindFolder();
        } else {
            icon.bindCategory(IconCategory.ofFileName(node.name()));
        }
        name.setText(node.name());
        meta.setText(formatMeta(node));
        itemView.setSelected(selected);
    }

    private String formatMeta(FileNode node) {
        if (node instanceof ParentFileNode) {
            return "Parent";
        }
        String size = node.isDirectory() ? "Folder" : ByteSize.format(node.sizeBytes());
        long mtime = node.lastModifiedMillis();
        if (mtime <= 0) {
            return size;
        }
        dateBuffer.setTime(mtime);
        return size + " · " + DATE_FMT.format(dateBuffer);
    }
}
