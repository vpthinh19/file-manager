package com.vpt.filemanager.storage.virtual;

/** Handle for stopping one virtual-location invalidation observer. */
@FunctionalInterface
public interface InvalidationSubscription {
    void close();
}
