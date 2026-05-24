package com.vpt.filemanager.browser.ui.pane.list;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.vpt.filemanager.R;
import com.vpt.filemanager.browser.item.Item;

public final class FileListAdapter extends ListAdapter<Item, FileViewHolder> {
    private static final Object SELECTION_PAYLOAD = new Object();
    private final Listener listener;
    private Set<String> selectedKeys = Collections.emptySet();

    public FileListAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void setSelection(Set<String> selection) {
        Set<String> next = selection == null ? Collections.emptySet() : selection;
        if (next.equals(selectedKeys)) {
            return;
        }
        Set<String> previous = selectedKeys;
        selectedKeys = next;
        for (int index = 0; index < getItemCount(); index++) {
            String key = getItem(index).key();
            if (previous.contains(key) != next.contains(key)) {
                notifyItemChanged(index, SELECTION_PAYLOAD);
            }
        }
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new FileViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_file_entry, parent, false), listener);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        Item entry = getItem(position);
        holder.bind(entry, selectedKeys.contains(entry.key()));
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.contains(SELECTION_PAYLOAD)) {
            holder.bindSelection(selectedKeys.contains(getItem(position).key()));
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onViewRecycled(@NonNull FileViewHolder holder) {
        holder.clear();
        super.onViewRecycled(holder);
    }

    public interface Listener {
        void onFileClicked(@NonNull Item entry);
        void onFileLongClicked(@NonNull Item entry);
    }

    private static final DiffUtil.ItemCallback<Item> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Item oldItem, @NonNull Item newItem) {
            return oldItem.key().equals(newItem.key());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Item oldItem, @NonNull Item newItem) {
            return oldItem.name().equals(newItem.name())
                    && oldItem.size() == newItem.size()
                    && oldItem.modifiedAt() == newItem.modifiedAt()
                    && oldItem.isFolder() == newItem.isFolder();
        }
    };
}
