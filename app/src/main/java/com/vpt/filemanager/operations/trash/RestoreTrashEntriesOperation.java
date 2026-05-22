package com.vpt.filemanager.operations.trash;

import androidx.annotation.NonNull;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.operations.result.BatchResult;
import com.vpt.filemanager.operations.trash.TrashStore;

/**
 * Restore selected trash entries by entry id.
 */
@Singleton
public final class RestoreTrashEntriesOperation {
    private final RestoreEntry restoreEntry;

    @Inject
    public RestoreTrashEntriesOperation(TrashStore trashStore) {
        this(trashStore::restore);
    }

    public RestoreTrashEntriesOperation(RestoreEntry restoreEntry) {
        this.restoreEntry = restoreEntry;
    }

    @NonNull
    public BatchResult execute(@NonNull List<String> entryIds) {
        int ok = 0;
        int failed = 0;
        String lastError = null;
        for (String id : entryIds) {
            try {
                restoreEntry.restore(id);
                ok++;
            } catch (NodeException e) {
                failed++;
                lastError = e.getMessage();
                timber.log.Timber.w(e, "Restore failed for entry: %s", id);
            }
        }
        return new BatchResult(ok, failed, lastError);
    }

    @FunctionalInterface
    public interface RestoreEntry {
        void restore(String entryId) throws NodeException;
    }
}
