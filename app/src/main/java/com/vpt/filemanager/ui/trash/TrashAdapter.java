package com.vpt.filemanager.ui.trash;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.ByteSize;
import com.vpt.filemanager.domain.model.TrashEntry;

/**
 * RecyclerView adapter for the trash screen. Pure view-binding — selection / restore semantics are
 * routed back to the fragment via {@link Listener}. DiffUtil keys off {@link TrashEntry#id}, which
 * is the Room primary key, so updates are stable across re-emits.
 */
public final class TrashAdapter extends ListAdapter<TrashEntry, TrashAdapter.Row> {
    public interface Listener {
        void onEntryClicked(@NonNull TrashEntry entry);
    }

    private final Listener listener;

    public TrashAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Row onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_trash_entry, parent, false);
        return new Row(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull Row holder, int position) {
        holder.bind(getItem(position));
    }

    static final class Row extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView name;
        private final TextView original;
        private final TextView meta;
        private final Listener listener;
        private TrashEntry current;

        Row(@NonNull View itemView, @NonNull Listener listener) {
            super(itemView);
            this.listener = listener;
            this.icon = itemView.findViewById(R.id.row_icon);
            this.name = itemView.findViewById(R.id.row_name);
            this.original = itemView.findViewById(R.id.row_original);
            this.meta = itemView.findViewById(R.id.row_meta);
            itemView.setOnClickListener(v -> {
                if (current != null) {
                    listener.onEntryClicked(current);
                }
            });
        }

        void bind(@NonNull TrashEntry entry) {
            current = entry;
            name.setText(entry.displayName);
            original.setText(entry.originalPath);
            icon.setImageResource(entry.directory
                    ? R.drawable.ic_folder
                    : R.drawable.ic_glyph_unknown);
            CharSequence when = DateUtils.getRelativeTimeSpanString(
                    entry.deletedAtMillis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS);
            // Folders show only the timestamp — a literal "Folder" label would be redundant
            // alongside the folder icon. Files show "<size> · <when>" since the size is the
            // useful extra signal.
            if (entry.directory) {
                meta.setText(when);
            } else {
                meta.setText(itemView.getContext().getString(
                        R.string.trash_meta_format, ByteSize.format(entry.sizeBytes), when));
            }
        }
    }

    private static final DiffUtil.ItemCallback<TrashEntry> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull TrashEntry oldItem, @NonNull TrashEntry newItem) {
            return oldItem.id.equals(newItem.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull TrashEntry oldItem, @NonNull TrashEntry newItem) {
            return oldItem.id.equals(newItem.id)
                    && oldItem.deletedAtMillis == newItem.deletedAtMillis
                    && oldItem.sizeBytes == newItem.sizeBytes
                    && oldItem.displayName.equals(newItem.displayName)
                    && oldItem.originalPath.equals(newItem.originalPath);
        }
    };
}
