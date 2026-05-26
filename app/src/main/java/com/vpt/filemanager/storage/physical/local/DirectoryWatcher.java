package com.vpt.filemanager.storage.physical.local;

import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.storage.virtual.InvalidationSubscription;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Watches device directories for change. One debounced {@link FileObserver} is shared by every
 * virtual location observing the same directory, so dual panes on the same folder don't multiply
 * refresh work. Callbacks are delivered on the main thread.
 */
final class DirectoryWatcher {
    private static final int EVENTS = FileObserver.CREATE | FileObserver.DELETE
            | FileObserver.MOVED_FROM | FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE
            | FileObserver.DELETE_SELF | FileObserver.MOVE_SELF;
    private static final long DEBOUNCE_MILLIS = 350L;

    @Nullable private Handler main;
    private final Map<String, Group> groups = new HashMap<>();

    @NonNull
    InvalidationSubscription watch(@NonNull File directory, @NonNull Runnable invalidated)
            throws FileOperationException {
        if (!directory.isDirectory()) throw new FileOperationException("Not a directory: " + directory);
        String key = directory.getAbsolutePath();
        synchronized (groups) {
            Group group = groups.get(key);
            if (group == null) {
                group = new Group(directory);
                groups.put(key, group);
                group.observer.startWatching();
            }
            group.callbacks.add(invalidated);
        }
        return () -> remove(key, invalidated);
    }

    private void remove(String key, Runnable callback) {
        synchronized (groups) {
            Group group = groups.get(key);
            if (group == null) return;
            group.callbacks.remove(callback);
            if (!group.callbacks.isEmpty()) return;
            group.observer.stopWatching();
            mainHandler().removeCallbacks(group.dispatch);
            groups.remove(key);
        }
    }

    private final class Group {
        private final Set<Runnable> callbacks = new LinkedHashSet<>();
        private final Runnable dispatch = () -> {
            List<Runnable> snapshot;
            synchronized (groups) {
                snapshot = new ArrayList<>(callbacks);
            }
            for (Runnable callback : snapshot) callback.run();
        };
        private final FileObserver observer;

        Group(File directory) {
            observer = new FileObserver(directory, EVENTS) {
                @Override
                public void onEvent(int event, @Nullable String name) {
                    mainHandler().removeCallbacks(dispatch);
                    mainHandler().postDelayed(dispatch, DEBOUNCE_MILLIS);
                }
            };
        }
    }

    private Handler mainHandler() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }
}
