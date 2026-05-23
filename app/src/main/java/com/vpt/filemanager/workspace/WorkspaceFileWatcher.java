package com.vpt.filemanager.workspace;

import android.os.FileObserver;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodePath;

import timber.log.Timber;

/**
 * Watches only live local directory snapshots. Events are invalidation hints; sources are read
 * again by workspace before UI receives a new snapshot.
 */
@Singleton
public final class WorkspaceFileWatcher {
    private static final int EVENTS = FileObserver.CREATE
            | FileObserver.DELETE
            | FileObserver.MOVED_FROM
            | FileObserver.MOVED_TO
            | FileObserver.CLOSE_WRITE
            | FileObserver.DELETE_SELF
            | FileObserver.MOVE_SELF;

    private final Map<NodePath, WatchEntry> entries = new HashMap<>();
    private Consumer<MutationResult> listener = ignored -> { };

    @Inject
    public WorkspaceFileWatcher() {
    }

    public synchronized void setListener(@NonNull Consumer<MutationResult> listener) {
        this.listener = listener;
    }

    public synchronized void retain(@NonNull NodePath path) {
        if (!path.isLocal()) {
            return;
        }
        WatchEntry current = entries.get(path);
        if (current != null) {
            current.retainCount++;
            return;
        }
        try {
            FileObserver observer = new FileObserver(new File(path.path()), EVENTS) {
                @Override
                public void onEvent(int event, String child) {
                    onFilesystemEvent(path, event);
                }
            };
            observer.startWatching();
            entries.put(path, new WatchEntry(observer));
        } catch (RuntimeException failure) {
            Timber.w(failure, "Cannot observe workspace path: %s", path);
        }
    }

    public synchronized void release(@NonNull NodePath path) {
        WatchEntry current = entries.get(path);
        if (current == null) {
            return;
        }
        current.retainCount--;
        if (current.retainCount <= 0) {
            current.observer.stopWatching();
            entries.remove(path);
        }
    }

    private void onFilesystemEvent(NodePath path, int event) {
        boolean selfRemoved = (event & (FileObserver.DELETE_SELF | FileObserver.MOVE_SELF)) != 0;
        MutationResult.Builder mutation = MutationResult.builder().changedContainer(path);
        if (selfRemoved) {
            mutation.removedSubtree(path);
        }
        Consumer<MutationResult> callback;
        synchronized (this) {
            callback = listener;
        }
        callback.accept(mutation.build());
    }

    private static final class WatchEntry {
        final FileObserver observer;
        int retainCount = 1;

        WatchEntry(FileObserver observer) {
            this.observer = observer;
        }
    }
}
