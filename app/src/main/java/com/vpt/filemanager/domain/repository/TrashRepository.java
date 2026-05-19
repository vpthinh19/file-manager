package com.vpt.filemanager.domain.repository;

import androidx.lifecycle.LiveData;

import java.util.List;

import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.model.TrashEntry;

public interface TrashRepository {
    /** Move {@code path} into the trash, preserving enough metadata to restore it later. */
    void moveToTrash(FilePath path) throws FileSystemException;

    /** Move the trashed item identified by {@code entryId} back to its original location. */
    void restore(String entryId) throws FileSystemException;

    /** Permanently delete every trashed item across all storage roots. */
    void empty() throws FileSystemException;

    /** Synchronous snapshot of every trashed item (newest first). For non-reactive callers. */
    List<TrashEntry> entries() throws FileSystemException;

    /** Reactive snapshot — Room emits whenever the underlying table changes. */
    LiveData<List<TrashEntry>> entriesLive();
}
