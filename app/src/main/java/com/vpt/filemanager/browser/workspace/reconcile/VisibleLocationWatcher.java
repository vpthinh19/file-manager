package com.vpt.filemanager.browser.workspace.reconcile;

import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.browser.item.Path;

/** Watches only locations currently on screen and coalesces filesystem bursts. */
@Singleton
public final class VisibleLocationWatcher {
    private static final int EVENTS = FileObserver.CREATE | FileObserver.DELETE
            | FileObserver.MOVED_FROM | FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE
            | FileObserver.DELETE_SELF | FileObserver.MOVE_SELF;
    private static final long COALESCE_MILLIS = 80L;
    private final Map<Path, WatchEntry> watched = new HashMap<>();
    private final Set<Path> changed = new LinkedHashSet<>();
    private final Set<String> removed = new LinkedHashSet<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable dispatchTask = this::dispatch;
    @Nullable private Listener listener;

    @Inject public VisibleLocationWatcher() {}

    public synchronized void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public synchronized void retain(@NonNull Path path) {
        Path observable = observed(path);
        if (observable == null) return;
        WatchEntry existing = watched.get(observable);
        if (existing != null) {
            existing.count++;
            return;
        }
        FileObserver observer = new FileObserver(new File(observable.directory()), EVENTS) {
            @Override public void onEvent(int event, String path) { receive(observable, event); }
        };
        observer.startWatching();
        watched.put(observable, new WatchEntry(observer));
    }

    public synchronized void release(@NonNull Path path) {
        Path observable = observed(path);
        WatchEntry entry = observable == null ? null : watched.get(observable);
        if (entry == null) return;
        if (--entry.count == 0) {
            entry.observer.stopWatching();
            watched.remove(observable);
        }
    }

    private synchronized void receive(Path path, int event) {
        changed.add(path);
        if ((event & (FileObserver.DELETE_SELF | FileObserver.MOVE_SELF)) != 0) {
            removed.add(path.directory());
        }
        main.removeCallbacks(dispatchTask);
        main.postDelayed(dispatchTask, COALESCE_MILLIS);
    }

    private void dispatch() {
        ChangeSet change;
        Listener current;
        synchronized (this) {
            change = new ChangeSet(changed, removed);
            changed.clear();
            removed.clear();
            current = listener;
        }
        if (current != null) current.onChanged(change);
    }

    @Nullable
    private static Path observed(Path path) {
        if (path.isStorage()) return path;
        if (path.isSearch()) return Path.storage(path.directory());
        if (path.isArchive()) {
            int separator = path.container().lastIndexOf('/');
            return Path.storage(separator <= 0 ? "/" : path.container().substring(0, separator));
        }
        return null;
    }

    public interface Listener {
        void onChanged(@NonNull ChangeSet change);
    }

    private static final class WatchEntry {
        final FileObserver observer;
        int count = 1;
        WatchEntry(FileObserver observer) { this.observer = observer; }
    }
}
