package com.vpt.filemanager.storage.bookmarks;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.storage.persistence.dao.BookmarkDao;
import com.vpt.filemanager.storage.persistence.entity.BookmarkRecord;
import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.storage.LocalStorageAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Persists user bookmarks; bookmark rows refer back to physical locations. */
@Singleton
public final class BookmarkCollection {
    private final BookmarkDao dao;
    private final LocalStorageAdapter storage;

    @Inject
    public BookmarkCollection(BookmarkDao dao, LocalStorageAdapter storage) {
        this.dao = dao;
        this.storage = storage;
    }

    public void add(@NonNull Entry entry) throws FileOperationException {
        if (entry.localPathOrNull() == null || !entry.isFolder() || entry.isInsideArchive()) {
            throw new FileOperationException("Only physical folders can be bookmarked");
        }
        if (dao.findByPath(entry.localPath()) != null) return;
        BookmarkRecord record = new BookmarkRecord();
        record.id = UUID.randomUUID().toString();
        record.path = entry.localPath();
        record.displayName = entry.name();
        record.addedAt = System.currentTimeMillis();
        Integer highest = dao.maxPosition();
        record.position = highest == null ? 0 : highest + 1;
        dao.insert(record);
    }

    public void remove(@NonNull Entry entry) {
        if (entry.localPathOrNull() != null) dao.deleteByPath(entry.localPath());
    }

    @NonNull
    public List<Entry> list() {
        List<Entry> result = new ArrayList<>();
        for (BookmarkRecord record : dao.all()) {
            File target = storage.fromAbsolutePath(record.path);
            if (!storage.exists(target)) {
                timber.log.Timber.w("Skipping broken bookmark: %s", record.path);
                continue;
            }
            try {
                result.add(Entry.bookmark(storage.pathOf(target), record.path, storage.name(target),
                        -1L, storage.modifiedAt(target)));
            } catch (FileOperationException invalidPath) {
                timber.log.Timber.w("Skipping bookmark outside storage: %s", record.path);
            }
        }
        return result;
    }
}
