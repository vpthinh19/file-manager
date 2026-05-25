package com.vpt.filemanager.ui.pane;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.vpt.filemanager.R;
import com.vpt.filemanager.model.Entry;

public final class EntryAdapter extends ListAdapter<Entry, EntryViewHolder> {
    private static final Object SELECTION_PAYLOAD = new Object();
    private final Listener listener;
    private Set<String> selectedKeys = Collections.emptySet();

    public EntryAdapter(Listener listener) {
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
    public EntryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new EntryViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_file_entry, parent, false), listener);
    }

    @Override
    public void onBindViewHolder(@NonNull EntryViewHolder holder, int position) {
        Entry entry = getItem(position);
        holder.bind(entry, selectedKeys.contains(entry.key()));
    }

    @Override
    public void onBindViewHolder(@NonNull EntryViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.contains(SELECTION_PAYLOAD)) {
            holder.bindSelection(selectedKeys.contains(getItem(position).key()));
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onViewRecycled(@NonNull EntryViewHolder holder) {
        holder.clear();
        super.onViewRecycled(holder);
    }

    public interface Listener {
        void onFileClicked(@NonNull Entry entry);
        void onFileLongClicked(@NonNull Entry entry);
    }

    private static final DiffUtil.ItemCallback<Entry> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Entry oldItem, @NonNull Entry newItem) {
            return oldItem.key().equals(newItem.key());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Entry oldItem, @NonNull Entry newItem) {
            return oldItem.name().equals(newItem.name())
                    && oldItem.size() == newItem.size()
                    && oldItem.modifiedAt() == newItem.modifiedAt()
                    && oldItem.isFolder() == newItem.isFolder();
        }
    };
}
