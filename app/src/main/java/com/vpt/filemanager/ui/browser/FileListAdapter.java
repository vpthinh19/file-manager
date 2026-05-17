package com.vpt.filemanager.ui.browser;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import java.util.Collections;
import java.util.Set;

import com.vpt.filemanager.R;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;

public final class FileListAdapter extends ListAdapter<FileNode, FileViewHolder> {
    private final Listener listener;
    private Set<FilePath> selectedPaths = Collections.emptySet();

    public FileListAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void setSelection(Set<FilePath> selection) {
        Set<FilePath> next = selection == null ? Collections.emptySet() : selection;
        if (next.equals(selectedPaths)) {
            return;
        }
        this.selectedPaths = next;
        notifyDataSetChanged();
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
        boolean selected = selectedPaths.contains(node.path());
        holder.bind(node, selected);
        holder.itemView.setOnClickListener(view -> listener.onFileClicked(node));
        holder.itemView.setOnLongClickListener(view -> {
            listener.onFileLongClicked(node);
            return true;
        });
    }

    public interface Listener {
        void onFileClicked(@NonNull FileNode node);

        void onFileLongClicked(@NonNull FileNode node);
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
