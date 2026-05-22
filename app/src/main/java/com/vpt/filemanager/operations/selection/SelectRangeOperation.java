package com.vpt.filemanager.operations.selection;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Fill the convex selection range between the first and last selected visible nodes.
 */
public final class SelectRangeOperation {
    public Output execute(Input input) {
        if (input.selection.size() < 2 || input.visibleNodes.isEmpty()) {
            return new Output(input.selection);
        }
        int minIdx = Integer.MAX_VALUE;
        int maxIdx = -1;
        for (int i = 0; i < input.visibleNodes.size(); i++) {
            if (input.selection.contains(input.visibleNodes.get(i).path())) {
                if (i < minIdx) minIdx = i;
                if (i > maxIdx) maxIdx = i;
            }
        }
        if (minIdx >= maxIdx) {
            return new Output(input.selection);
        }
        LinkedHashSet<NodePath> next = new LinkedHashSet<>(input.selection);
        for (int i = minIdx; i <= maxIdx; i++) {
            next.add(input.visibleNodes.get(i).path());
        }
        return new Output(Collections.unmodifiableSet(next));
    }

    public static final class Input {
        @NonNull public final Set<NodePath> selection;
        @NonNull public final List<VirtualNode> visibleNodes;

        public Input(@NonNull Set<NodePath> selection,
                     @NonNull List<VirtualNode> visibleNodes) {
            this.selection = Set.copyOf(selection);
            this.visibleNodes = List.copyOf(visibleNodes);
        }
    }

    public static final class Output {
        @NonNull public final Set<NodePath> selection;

        private Output(@NonNull Set<NodePath> selection) {
            this.selection = Set.copyOf(selection);
        }
    }
}
