package com.vpt.filemanager.storage.trash;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.storage.persistence.dao.TrashDao;
import com.vpt.filemanager.storage.persistence.entity.TrashRecord;
import com.vpt.filemanager.entry.Entry;
import com.vpt.filemanager.storage.LocalStorageAdapter;

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
        if (entry.localPathOrNull() == null || entry.isArchiveEntry()) {
            throw new FileOperationException("Entry cannot be moved to trash");
        }
        File source = new File(entry.localPath());
        if (!source.exists()) throw new FileOperationException("File no longer exists: " + entry.name());
        String id = UUID.randomUUID().toString();
        File destination = new File(new File(trashRoot(), id), entry.name());
        File parent = destination.getParentFile();
        if (parent == null || !parent.mkdirs() && !parent.isDirectory()) {
            throw new FileOperationException("Cannot prepare trash");
        }
        storage.move(source, destination);
        TrashRecord record = new TrashRecord();
        record.id = id;
        record.originalPath = entry.localPath();
        record.storedPath = destination.getAbsolutePath().replace('\\', '/');
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
        File destination = new File(record.originalPath);
        if (destination.exists()) {
            throw new FileOperationException("Cannot restore, destination exists: " + record.displayName);
        }
        File parent = destination.getParentFile();
        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
            throw new FileOperationException("Cannot restore parent directory");
        }
        storage.move(new File(record.storedPath), destination);
        dao.deleteById(id);
    }

    public void deletePermanently(@NonNull Entry entry) throws FileOperationException {
        if (!entry.isTrashItem() || entry.recordId() == null) return;
        storage.deletePermanently(new File(entry.localPath()));
        dao.deleteById(entry.recordId());
    }

    public void empty() throws FileOperationException {
        File root = trashRoot();
        if (root.exists()) storage.deletePermanently(root);
        dao.deleteAll();
    }

    private File trashRoot() {
        return new File(storage.rootDirectory(), ".FileManagerTrash");
    }
}
