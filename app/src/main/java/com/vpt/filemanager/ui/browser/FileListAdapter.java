package com.vpt.filemanager.ui.browser;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import com.vpt.filemanager.R;
import com.vpt.filemanager.domain.model.FileNode;

public final class FileListAdapter extends ListAdapter<FileNode, FileViewHolder> {
    private final int pane;
    private final Listener listener;

    public FileListAdapter(int pane, Listener listener) {
        super(DIFF);
        this.pane = pane;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new FileViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_file_node, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileNode node = getItem(position);
        holder.bind(node);
        holder.itemView.setOnClickListener(view -> listener.onFileClicked(pane, node));
        holder.itemView.setOnLongClickListener(view -> {
            listener.onFileLongClicked(pane, node);
            return true;
        });
    }

    public interface Listener {
        void onFileClicked(int pane, FileNode node);

        void onFileLongClicked(int pane, FileNode node);
    }

    private static final DiffUtil.ItemCallback<FileNode> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull FileNode oldItem, @NonNull FileNode newItem) {
            return oldItem.path().equals(newItem.path());
        }

        @Override
        public boolean areContentsTheSame(@NonNull FileNode oldItem, @NonNull FileNode newItem) {
            return oldItem.name().equals(newItem.name())
                    && oldItem.sizeBytes() == newItem.sizeBytes()
                    && oldItem.lastModifiedMillis() == newItem.lastModifiedMillis()
                    && oldItem.isDirectory() == newItem.isDirectory();
        }
    };
}
