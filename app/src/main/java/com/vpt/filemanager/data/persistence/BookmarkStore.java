package com.vpt.filemanager.data.persistence;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.item.ItemFactory;
import com.vpt.filemanager.data.local.LocalStorageAdapter;
import com.vpt.filemanager.data.persistence.dao.BookmarkDao;
import com.vpt.filemanager.data.persistence.entity.BookmarkRecord;

@Singleton
public final class BookmarkStore {
    private final BookmarkDao dao;
    private final LocalStorageAdapter files;
    private final ItemFactory items;

    @Inject
    public BookmarkStore(BookmarkDao dao, LocalStorageAdapter files, ItemFactory items) {
        this.dao = dao;
        this.files = files;
        this.items = items;
    }

    public void add(@NonNull Item item) throws FileOperationException {
        if (!item.isLocalActionTarget() || !item.isFolder()) {
            throw new FileOperationException("Only local folders can be bookmarked");
        }
        if (dao.findByPath(item.localPath()) != null) return;
        BookmarkRecord record = new BookmarkRecord();
        record.id = UUID.randomUUID().toString();
        record.path = item.localPath();
        record.displayName = item.name();
        record.addedAt = System.currentTimeMillis();
        Integer highest = dao.maxPosition();
        record.position = highest == null ? 0 : highest + 1;
        dao.insert(record);
    }

    public void remove(@NonNull Item item) {
        if (item.localPathOrNull() != null) dao.deleteByPath(item.localPath());
    }

    @NonNull
    public List<Item> list() {
        List<Item> output = new ArrayList<>();
        for (BookmarkRecord record : dao.all()) {
            try {
                Item target = files.inspect(record.path);
                output.add(items.bookmark(target.localPath(), target.name(), target.isFolder(),
                        target.size(), target.modifiedAt()));
            } catch (FileOperationException broken) {
                timber.log.Timber.w(broken, "Skipping broken bookmark: %s", record.path);
            }
        }
        return output;
    }
}
