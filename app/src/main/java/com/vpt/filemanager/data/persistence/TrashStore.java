package com.vpt.filemanager.data.persistence;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.data.persistence.dao.TrashDao;
import com.vpt.filemanager.data.persistence.entity.TrashRecord;
import com.vpt.filemanager.browser.rule.StorageBoundary;

@Singleton
public final class TrashStore {
    private final TrashDao dao;

    @Inject
    public TrashStore(TrashDao dao) {
        this.dao = dao;
    }

    public void put(@NonNull Item item) throws FileOperationException {
        if (!item.isLocalActionTarget()) throw new FileOperationException("Item cannot be trashed");
        Path source = Paths.get(item.localPath());
        if (!Files.exists(source)) throw new FileOperationException("File no longer exists: " + item.name());
        String id = UUID.randomUUID().toString();
        Path destination = trashRoot().resolve(id).resolve(item.name());
        try {
            Files.createDirectories(destination.getParent());
            movePath(source, destination);
        } catch (IOException error) {
            throw new FileOperationException("Move to trash failed: " + item.name(), error);
        }
        TrashRecord record = new TrashRecord();
        record.id = id;
        record.originalPath = source.toString().replace('\\', '/');
        record.storedPath = destination.toString().replace('\\', '/');
        record.displayName = item.name();
        record.deletedAt = System.currentTimeMillis();
        record.sizeBytes = item.isFolder() ? -1 : item.size();
        record.directory = item.isFolder();
        dao.insert(record);
    }

    @NonNull
    public List<Item> list() {
        List<Item> items = new ArrayList<>();
        for (TrashRecord record : dao.all()) {
            items.add(Item.trash(record.id, record.storedPath, record.displayName,
                    record.directory, record.sizeBytes, record.deletedAt));
        }
        return items;
    }

    public void restore(String id) throws FileOperationException {
        TrashRecord record = dao.findById(id);
        if (record == null) throw new FileOperationException("Trash entry no longer exists");
        Path source = Paths.get(record.storedPath);
        Path destination = Paths.get(record.originalPath);
        if (Files.exists(destination)) {
            throw new FileOperationException("Cannot restore, destination exists: " + record.displayName);
        }
        try {
            if (destination.getParent() != null) Files.createDirectories(destination.getParent());
            movePath(source, destination);
            deleteIfEmpty(source.getParent());
            dao.deleteById(id);
        } catch (IOException error) {
            throw new FileOperationException("Restore failed: " + record.displayName, error);
        }
    }

    public void empty() throws FileOperationException {
        try {
            deleteRecursively(trashRoot());
            dao.deleteAll();
        } catch (IOException error) {
            throw new FileOperationException("Empty trash failed", error);
        }
    }

    private static Path trashRoot() {
        return Paths.get(StorageBoundary.ROOT_PATH, ".FileManagerTrash");
    }

    private static void movePath(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(source, destination);
        }
    }

    private static void deleteIfEmpty(Path path) throws IOException {
        if (path == null || !Files.isDirectory(path)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            if (!stream.iterator().hasNext()) Files.delete(path);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        if (Files.isDirectory(path) && !Files.isSymbolicLink(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) deleteRecursively(child);
            }
        }
        Files.deleteIfExists(path);
    }
}
