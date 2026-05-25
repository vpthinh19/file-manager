package com.vpt.filemanager.storage.index;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.storage.persistence.dao.BookmarkDao;
import com.vpt.filemanager.storage.persistence.entity.BookmarkRecord;
import com.vpt.filemanager.model.Entry;
import com.vpt.filemanager.model.Location;
import com.vpt.filemanager.resolver.EntryFactory;
import com.vpt.filemanager.storage.LocalStorageAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Persists user bookmarks; bookmark rows refer back to physical locations. */
@Singleton
public final class BookmarkIndex {
    private final BookmarkDao dao;
    private final LocalStorageAdapter storage;
    private final EntryFactory entries;

    @Inject
    public BookmarkIndex(BookmarkDao dao, LocalStorageAdapter storage, EntryFactory entries) {
        this.dao = dao;
        this.storage = storage;
        this.entries = entries;
    }

    public void add(@NonNull Entry entry) throws FileOperationException {
        if (entry.localPathOrNull() == null || !entry.isFolder() || entry.isArchiveEntry()) {
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
            File target = new File(record.path);
            if (!target.exists()) {
                timber.log.Timber.w("Skipping broken bookmark: %s", record.path);
                continue;
            }
            Entry raw = entries.physical(target);
            result.add(Entry.bookmark(Location.storage(record.path), raw.name(), raw.isFolder(),
                    raw.size(), raw.modifiedAt()));
        }
        return result;
    }
}
