package com.vpt.filemanager.ui.pane.list;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.vpt.filemanager.R;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;

/**
 * ListAdapter cho {@link VirtualNode} rows. Phase R-5b migrated từ FileNode → VirtualNode.
 * Selection tracked qua {@code Set<NodePath>} — path là identity, không phải node instance, nên
 * back/forward stack hoặc refresh không invalidate selection (path-based comparison).
 *
 * <p>DiffUtil compare:
 * <ul>
 *   <li>{@code areItemsTheSame}: same path → same row identity</li>
 *   <li>{@code areContentsTheSame}: name + size + modifiedAt + isFolder identical → no rebind</li>
 * </ul>
 */
public final class FileListAdapter extends ListAdapter<VirtualNode, FileViewHolder> {
    private static final Object SELECTION_PAYLOAD = new Object();
    private final Listener listener;
    private Set<NodePath> selectedPaths = Collections.emptySet();

    public FileListAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    /**
     * Apply new selection set. Avoid {@code notifyDataSetChanged} — forces every visible row
     * rebind + kills item animator. Instead walk current list once, only invalidate rows whose
     * selection state flipped.
     */
    public void setSelection(Set<NodePath> selection) {
        Set<NodePath> next = selection == null ? Collections.emptySet() : selection;
        if (next.equals(selectedPaths)) {
            return;
        }
        Set<NodePath> previous = this.selectedPaths;
        this.selectedPaths = next;
        int count = getItemCount();
        for (int i = 0; i < count; i++) {
            NodePath path = getItem(i).path();
            boolean wasSelected = previous.contains(path);
            boolean isSelected = next.contains(path);
            if (wasSelected != isSelected) {
                notifyItemChanged(i, SELECTION_PAYLOAD);
            }
        }
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Click listeners attach trong VH ctor (1 lần per VH) thay vì onBindViewHolder — bỏ
        // per-bind lambda allocation churn GC khi scroll.
        return new FileViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_file_node, parent, false), listener);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        VirtualNode node = getItem(position);
        holder.bind(node, selectedPaths.contains(node.path()));
    }

    @Override
    public void onBindViewHolder(
            @NonNull FileViewHolder holder,
            int position,
            @NonNull List<Object> payloads) {
        if (payloads.contains(SELECTION_PAYLOAD)) {
            holder.bindSelection(selectedPaths.contains(getItem(position).path()));
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    public interface Listener {
        void onFileClicked(@NonNull VirtualNode node);

        void onFileLongClicked(@NonNull VirtualNode node);
    }

    private static final DiffUtil.ItemCallback<VirtualNode> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull VirtualNode oldItem, @NonNull VirtualNode newItem) {
            return oldItem.path().equals(newItem.path());
        }

        @Override
        public boolean areContentsTheSame(@NonNull VirtualNode oldItem, @NonNull VirtualNode newItem) {
            return oldItem.name().equals(newItem.name())
                    && oldItem.size() == newItem.size()
                    && oldItem.modifiedAt() == newItem.modifiedAt()
                    && oldItem.isFolder() == newItem.isFolder();
        }
    };
}
