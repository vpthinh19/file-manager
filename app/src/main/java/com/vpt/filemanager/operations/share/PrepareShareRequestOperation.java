package com.vpt.filemanager.operations.share;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import com.vpt.filemanager.node.VirtualNode;

/**
 * Select shareable local nodes from a node list.
 */
public final class PrepareShareRequestOperation {
    public ShareRequest execute(Input input) {
        List<com.vpt.filemanager.node.NodePath> paths = new ArrayList<>();
        for (VirtualNode node : input.nodes) {
            if (node.path().isLocal()) {
                paths.add(node.path());
            }
        }
        return new ShareRequest(paths);
    }

    public static final class Input {
        @NonNull public final List<VirtualNode> nodes;

        public Input(@NonNull List<VirtualNode> nodes) {
            this.nodes = List.copyOf(nodes);
        }
    }
}
