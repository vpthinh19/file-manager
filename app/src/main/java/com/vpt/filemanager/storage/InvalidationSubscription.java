package com.vpt.filemanager.storage;

/** Handle for stopping one virtual-location invalidation observer. */
@FunctionalInterface
public interface InvalidationSubscription {
    void close();
}
