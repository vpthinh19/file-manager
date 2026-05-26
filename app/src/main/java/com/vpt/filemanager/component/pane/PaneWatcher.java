package com.vpt.filemanager.component.pane;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.storage.facade.StorageFacade;
import com.vpt.filemanager.storage.virtual.InvalidationSubscription;

/**
 * Owns one filesystem-change subscription for the pane's current location. It re-subscribes only
 * when the location actually changes, so reloads that stay in the same folder (a refresh, a sort
 * change) don't tear down and rebuild the watcher.
 */
final class PaneWatcher {
    private final StorageFacade facade;
    private final Runnable onChanged;
    @Nullable private InvalidationSubscription subscription;
    @Nullable private Path watched;

    PaneWatcher(@NonNull StorageFacade facade, @NonNull Runnable onChanged) {
        this.facade = facade;
        this.onChanged = onChanged;
    }

    void watch(@NonNull Path location) {
        if (location.equals(watched)) return;
        close();
        watched = location;
        try {
            subscription = facade.observe(location, onChanged);
        } catch (Exception ignored) {
            // Listing reports actionable errors; observation is optional.
            watched = null;
        }
    }

    void close() {
        if (subscription != null) subscription.close();
        subscription = null;
        watched = null;
    }
}
