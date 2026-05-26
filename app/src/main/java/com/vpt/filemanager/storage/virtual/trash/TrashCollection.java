package com.vpt.filemanager.storage.virtual.trash;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.storage.persistence.dao.TrashDao;
import com.vpt.filemanager.storage.persistence.entity.TrashRecord;
import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.storage.physical.local.LocalStorageAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Trash is a persisted collection whose payload remains physical storage data. */
@Singleton
public final class TrashCollection {
    private final TrashDao dao;
    private final LocalStorageAdapter storage;

    @Inject
    public TrashCollection(TrashDao dao, LocalStorageAdapter storage) {
        this.dao = dao;
        this.storage = storage;
    }

    public void put(@NonNull Entry entry) throws FileOperationException {
        if (entry.localPathOrNull() == null || entry.isInsideArchive()) {
            throw new FileOperationException("Entry cannot be moved to trash");
        }
        File source = storage.fromAbsolutePath(entry.localPath());
        if (!storage.exists(source)) throw new FileOperationException("File no longer exists: " + entry.name());
        String id = UUID.randomUUID().toString();
        File destination = storage.target(storage.target(trashRoot(), id), entry.name());
        storage.ensureDirectory(storage.parent(destination));
        storage.move(source, destination);
        TrashRecord record = new TrashRecord();
        record.id = id;
        record.originalPath = entry.localPath();
        record.storedPath = storage.absolutePath(destination);
        record.displayName = entry.name();
        record.deletedAt = System.currentTimeMillis();
        record.sizeBytes = entry.isFolder() ? -1L : entry.size();
        record.directory = entry.isFolder();
        dao.insert(record);
    }

    @NonNull
    public List<Entry> list() {
        List<Entry> result = new ArrayList<>();
        for (TrashRecord record : dao.all()) {
            result.add(Entry.trash(record.id, record.storedPath, record.displayName,
                    record.directory, record.sizeBytes, record.deletedAt));
        }
        return result;
    }

    public void restore(@NonNull String id) throws FileOperationException {
        TrashRecord record = dao.findById(id);
        if (record == null) throw new FileOperationException("Trash entry no longer exists");
        File destination = storage.fromAbsolutePath(record.originalPath);
        if (storage.exists(destination)) {
            throw new FileOperationException("Cannot restore, destination exists: " + record.displayName);
        }
        storage.ensureDirectory(storage.parent(destination));
        storage.move(storage.fromAbsolutePath(record.storedPath), destination);
        dao.deleteById(id);
    }

    public void deletePermanently(@NonNull Entry entry) throws FileOperationException {
        if (!entry.isTrashItem() || entry.recordId() == null) return;
        storage.deletePermanently(storage.fromAbsolutePath(entry.localPath()));
        dao.deleteById(entry.recordId());
    }

    public void empty() throws FileOperationException {
        File root = trashRoot();
        if (storage.exists(root)) storage.deletePermanently(root);
        dao.deleteAll();
    }

    private File trashRoot() throws FileOperationException {
        return storage.target(storage.rootDirectory(), ".FileManagerTrash");
    }
}
