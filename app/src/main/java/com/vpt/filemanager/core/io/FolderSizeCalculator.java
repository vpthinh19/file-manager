package com.vpt.filemanager.core.io;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.core.concurrent.AppExecutors;
import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.repository.FileRepository;

/**
 * Walks the file tree under a path and sums every leaf's size. This is a Visitor over the Composite
 * {@link FileNode} tree — leaves contribute their own byte count, composites (folders) delegate to
 * their children. The traversal lives in this service (not on FileNode itself) because our
 * composite is lazy: subtrees are loaded on demand via {@link FileRepository#list}, and we want to
 * keep the domain layer free of I/O.
 *
 * <p>Implementation uses an iterative depth-first walk with an explicit deque so deeply nested
 * directories don't blow the call stack. Unreadable subtrees (permission denied) are skipped
 * silently — partial size is more useful than an outright failure.
 */
@Singleton
public final class FolderSizeCalculator {
    private final FileRepository fileRepository;
    private final AppExecutors executors;

    @Inject
    public FolderSizeCalculator(FileRepository fileRepository, AppExecutors executors) {
        this.fileRepository = fileRepository;
        this.executors = executors;
    }

    /**
     * Compute recursive size on a background thread.
     *
     * @param start root of the walk (file or folder)
     * @return future resolving to total bytes; a file resolves to its own length; an unreadable
     *         start resolves to {@code 0}
     */
    public Future<Long> compute(FilePath start) {
        return executors.computation().submit(() -> walk(start));
    }

    private long walk(FilePath start) {
        try {
            FileNode root = fileRepository.resolve(start);
            if (!root.isDirectory()) {
                long size = root.sizeBytes();
                return size > 0 ? size : 0;
            }
        } catch (FileSystemException e) {
            return 0;
        }
        long total = 0;
        Deque<FilePath> pending = new ArrayDeque<>();
        pending.push(start);
        while (!pending.isEmpty()) {
            FilePath dir = pending.pop();
            try {
                List<FileNode> children = fileRepository.list(dir);
                for (FileNode child : children) {
                    if (child.isDirectory()) {
                        pending.push(child.path());
                    } else {
                        long size = child.sizeBytes();
                        if (size > 0) {
                            total += size;
                        }
                    }
                }
            } catch (FileSystemException ignored) {
                // Skip subtree we can't read; the partial total is still useful.
            }
        }
        return total;
    }
}
