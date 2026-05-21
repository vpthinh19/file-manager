package com.vpt.filemanager.browser;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.Date;

import com.vpt.filemanager.R;
import com.vpt.filemanager.util.ByteSize;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Render 1 {@link VirtualNode} row. Phase R-5b: migrated từ FileNode → VirtualNode + dùng
 * {@link VirtualNode#isParent()} thay {@code instanceof ParentFileNode}.
 *
 * <p>Folder rows lấy folder badge; file rows lookup {@link IconCategory#ofFileName(String)}.
 * Selection state propagate qua {@link View#setSelected(boolean)} → {@code bg_file_row} pick
 * state-list color.
 *
 * <p>Perf: click listeners attach 1 lần trong ctor (read latest bound node qua {@link #currentNode});
 * trước đây {@code onBindViewHolder} alloc lambda mỗi bind, churn GC khi scroll.
 * {@link #DATE_FMT} là class-level static — {@code DateFormat.getDateTimeInstance(...)} internally
 * walk Locale + alloc Calendar, quá nặng cho bind hot path.
 */
public final class FileViewHolder extends RecyclerView.ViewHolder {
    /**
     * Shared formatter. {@link DateFormat} KHÔNG thread-safe nhưng RV bind luôn trên main thread,
     * nên 1 static instance an toàn + tránh per-bind allocation.
     */
    private static final DateFormat DATE_FMT =
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

    private final FileIconView icon;
    private final TextView name;
    private final TextView meta;
    private final Date dateBuffer = new Date();
    private VirtualNode currentNode;

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

    public void bind(VirtualNode node, boolean selected) {
        currentNode = node;
        if (node.isParent() || node.isFolder()) {
            icon.bindFolder();
        } else {
            icon.bindCategory(IconCategory.ofFileName(node.name()));
        }
        // Parent marker hiển thị ".." thay vì path.name() (vì path.name() = tên folder thật của parent).
        name.setText(node.isParent() ? ".." : node.name());
        meta.setText(formatMeta(node));
        itemView.setSelected(selected);
    }

    private String formatMeta(VirtualNode node) {
        if (node.isParent()) {
            return "Parent";
        }
        String size = node.isFolder() ? "Folder" : ByteSize.format(node.size());
        long mtime = node.modifiedAt();
        if (mtime <= 0) {
            return size;
        }
        dateBuffer.setTime(mtime);
        return size + " · " + DATE_FMT.format(dateBuffer);
    }
}
