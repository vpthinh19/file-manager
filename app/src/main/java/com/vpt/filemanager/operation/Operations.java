package com.vpt.filemanager.operation;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.storage.Storage;
import com.vpt.filemanager.storage.StorageRegistry;
import com.vpt.filemanager.storage.archive.ArchiveAccess;
import com.vpt.filemanager.storage.bookmarks.BookmarkCollection;
import com.vpt.filemanager.storage.trash.TrashCollection;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Facade for component-issued mutations. It never hard-codes a backend: it asks
 * the {@link StorageRegistry} which {@link Storage} owns a path and delegates the
 * generic operations (create, rename, delete, copy/move within a backend).
 *
 * <p>Two responsibilities stay here on purpose — as <em>product policy</em>, not
 * backend knowledge:
 * <ul>
 *   <li>{@link #delete} sends real files to Trash (a soft delete) while it
 *       destroys archive-internal and already-trashed entries outright.</li>
 *   <li>Cross-format transfer between local storage and an archive wraps the
 *       libarchive import/extract bridge ({@link ArchiveAccess}); every other
 *       transfer is polymorphic via {@code copyInternal}/{@code moveInternal}.</li>
 * </ul>
 * Trash and Bookmarks expose collection-only operations (empty/restore/add/
 * remove) that are not part of the generic {@link Storage} contract, so those
 * still go through {@link TrashCollection}/{@link BookmarkCollection}.
 */
@Singleton
public final class Operations {
    private final StorageRegistry registry;
    private final TrashCollection trash;
    private final BookmarkCollection bookmarks;
    private final ArchiveAccess archive;

    @Inject
    public Operations(StorageRegistry registry, TrashCollection trash,
                      BookmarkCollection bookmarks, ArchiveAccess archive) {
        this.registry = registry;
        this.trash = trash;
        this.bookmarks = bookmarks;
        this.archive = archive;
    }

    public boolean canWrite(@NonNull Path location) {
        try {
            return registry.storageFor(location).canWrite(location);
        } catch (FileOperationException error) {
            return false;
        }
    }

    public void create(@NonNull Path parent, @NonNull String name, boolean folder)
            throws FileOperationException {
        validName(name);
        Storage storage = registry.storageFor(parent);
        if (!storage.canWrite(parent)) throw new FileOperationException("Path is read-only");
        storage.create(parent, name.trim(), folder);
    }

    public void rename(@NonNull Entry entry, @NonNull String name) throws FileOperationException {
        validName(name);
        registry.storageFor(entry.path()).rename(entry, name.trim());
    }

    public void delete(@NonNull List<Entry> selected) throws FileOperationException {
        for (Entry entry : selected) {
            if (entry.isParent()) continue;
            if (entry.isInsideArchive() || entry.isTrashItem()) {
                // Archive-internal and already-trashed entries are removed for good.
                registry.storageFor(entry.path()).delete(List.of(entry));
            } else {
                // A real file or folder is moved to Trash rather than destroyed.
                trash.put(entry);
            }
        }
    }

    public void restore(@NonNull List<Entry> selected) throws FileOperationException {
        for (Entry entry : selected) {
            if (entry.recordId() != null) trash.restore(entry.recordId());
        }
    }

    public void emptyTrash() throws FileOperationException {
        trash.empty();
    }

    public void bookmark(@NonNull Entry entry) throws FileOperationException {
        bookmarks.add(entry);
    }

    public void removeBookmarks(@NonNull List<Entry> selected) {
        for (Entry entry : selected) bookmarks.remove(entry);
    }

    public void transfer(@NonNull List<Entry> selected, @NonNull Path destination, boolean move)
            throws FileOperationException {
        Storage destinationStorage = registry.storageFor(destination);
        if (!destinationStorage.canWrite(destination)) {
            throw new FileOperationException("Destination is read-only");
        }
        for (Entry source : selected) {
            if (source.isParent()) continue;
            Storage sourceStorage = registry.storageFor(source.path());
            String name = uniqueName(destinationStorage, destination, source.name());
            if (sourceStorage == destinationStorage) {
                if (move) sourceStorage.moveInternal(source, destination, name);
                else sourceStorage.copyInternal(source, destination, name);
            } else if (destination.isInsideArchive()) {
                // local -> archive: append the file/tree into the container.
                archive.importFromStorage(destination, source, name, false);
                if (move) sourceStorage.delete(List.of(source));
            } else {
                // archive -> local: extract the entry (file or folder tree) out.
                File target = new File(destinationStorage.materialize(destination), name);
                archive.extractToStorage(source, target.getAbsolutePath());
                if (move) sourceStorage.delete(List.of(source));
            }
        }
    }

    public String materializeIfRequired(@NonNull Entry entry) throws FileOperationException {
        if (entry.isInsideArchive()) {
            return registry.storageFor(entry.path()).materialize(entry.path()).getAbsolutePath();
        }
        // Local and trashed entries already point at a real backing file.
        return entry.localPath();
    }

    private String uniqueName(Storage storage, Path destination, String name)
            throws FileOperationException {
        Set<String> existing = new HashSet<>();
        for (Entry entry : storage.list(destination)) existing.add(entry.name());
        if (!existing.contains(name)) return name;
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String suffix = dot > 0 ? name.substring(dot) : "";
        int index = 1;
        String candidate;
        do {
            candidate = stem + " (" + index++ + ")" + suffix;
        } while (existing.contains(candidate));
        return candidate;
    }

    private static void validName(String value) throws FileOperationException {
        if (value == null || value.isBlank() || value.contains("/") || value.contains("\\")) {
            throw new FileOperationException("Invalid name");
        }
    }
}
