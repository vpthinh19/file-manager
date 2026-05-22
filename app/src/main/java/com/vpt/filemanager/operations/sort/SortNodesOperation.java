package com.vpt.filemanager.operations.sort;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import com.vpt.filemanager.operations.sort.SortOrder;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Sort visible virtual nodes without touching filesystem state.
 */
public final class SortNodesOperation {
    public Output execute(Input input) {
        List<VirtualNode> sorted = new ArrayList<>(input.nodes);
        sorted.sort(input.order.folderFirstComparator());
        return new Output(sorted);
    }

    public static final class Input {
        @NonNull public final List<VirtualNode> nodes;
        @NonNull public final SortOrder order;

        public Input(@NonNull List<VirtualNode> nodes, @NonNull SortOrder order) {
            this.nodes = List.copyOf(nodes);
            this.order = order;
        }
    }

    public static final class Output {
        @NonNull public final List<VirtualNode> nodes;

        private Output(@NonNull List<VirtualNode> nodes) {
            this.nodes = List.copyOf(nodes);
        }
    }
}
