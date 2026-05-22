package com.vpt.filemanager.properties;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.FilePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodeFactory;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.threading.AppExecutors;

/**
 * Walk cây node + sum tổng size từng leaf. Visitor over Composite {@link VirtualNode} tree —
 * leaves contribute byte count, folders delegate xuống children. Traversal sống ở service (không
 * trên VirtualNode) vì composite lazy: subtrees fetch on-demand qua {@link VirtualNode#children()};
 * giữ model layer free of I/O scheduling.
 *
 * <p>Iterative depth-first walk với explicit deque → deeply nested directories không blow call
 * stack. Subtree không đọc được (permission denied / archive corrupt) silently skip — partial
 * size useful hơn outright failure.
 *
 * <p>Phase R-6: migrated từ {@code FileRepository} → {@link NodeFactory}. Walk identical, chỉ
 * underlying API thay đổi.
 */
@Singleton
public final class FolderSizeCalculator {
    private final NodeFactory nodeFactory;
    private final AppExecutors executors;

    @Inject
    public FolderSizeCalculator(NodeFactory nodeFactory, AppExecutors executors) {
        this.nodeFactory = nodeFactory;
        this.executors = executors;
    }

    /**
     * Compute recursive size trên computation pool.
     *
     * @param start root walk (file or folder)
     * @return future trả về total bytes; file → own length; unreadable start → {@code 0}
     */
    public Future<Long> compute(FilePath start) {
        return executors.computation().submit(() -> walk(start));
    }

    private long walk(FilePath start) {
        VirtualNode root;
        try {
            root = nodeFactory.fromPath(start);
        } catch (NodeException e) {
            return 0;
        }
        if (!root.isFolder()) {
            long size = root.size();
            return size > 0 ? size : 0;
        }
        long total = 0;
        Deque<VirtualNode> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            VirtualNode dir = pending.pop();
            try {
                List<VirtualNode> children = dir.children();
                for (VirtualNode child : children) {
                    if (child.isFolder()) {
                        pending.push(child);
                    } else {
                        long size = child.size();
                        if (size > 0) {
                            total += size;
                        }
                    }
                }
            } catch (NodeException ignored) {
                // Skip subtree không đọc được; partial total vẫn useful.
            }
        }
        return total;
    }
}
