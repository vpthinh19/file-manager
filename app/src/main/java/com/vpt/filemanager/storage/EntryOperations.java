package com.vpt.filemanager.storage;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.handler.archive.ArchiveHandler;
import com.vpt.filemanager.model.Entry;
import com.vpt.filemanager.model.Location;
import com.vpt.filemanager.resolver.EntryFactory;
import com.vpt.filemanager.storage.index.BookmarkIndex;
import com.vpt.filemanager.storage.index.TrashIndex;

import java.io.File;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Shared physical/archive mutations called by the component that owns an interaction. */
@Singleton
public final class EntryOperations {
    private final LocalStorageAdapter local;
    private final ArchiveHandler archive;
    private final TrashIndex trash;
    private final BookmarkIndex bookmarks;
    private final EntryFactory entries;

    @Inject
    public EntryOperations(LocalStorageAdapter local, ArchiveHandler archive, TrashIndex trash,
                           BookmarkIndex bookmarks, EntryFactory entries) {
        this.local = local;
        this.archive = archive;
        this.trash = trash;
        this.bookmarks = bookmarks;
        this.entries = entries;
    }

    public boolean canWrite(@NonNull Location location) {
        return location.isStorage() && (!location.isArchiveEntry() || archive.canWrite(location));
    }

    public void create(@NonNull Location parent, @NonNull String name, boolean folder)
            throws FileOperationException {
        validName(name);
        if (!canWrite(parent)) throw new FileOperationException("Location is read-only");
        if (parent.isArchiveEntry()) {
            archive.create(parent, name.trim(), folder);
        } else {
            local.create(new File(parent.physicalPath()), name.trim(), folder);
        }
    }

    public void rename(@NonNull Entry entry, @NonNull String name) throws FileOperationException {
        validName(name);
        if (entry.isArchiveEntry()) archive.rename(entry, name.trim());
        else if (entry.localPathOrNull() != null) local.rename(new File(entry.localPath()), name.trim());
        else throw new FileOperationException("Entry cannot be renamed");
    }

    public void delete(@NonNull List<Entry> selected) throws FileOperationException {
        List<Entry> archiveItems = selected.stream().filter(Entry::isArchiveEntry).toList();
        if (!archiveItems.isEmpty()) archive.delete(archiveItems);
        for (Entry entry : selected) {
            if (entry.isArchiveEntry() || entry.isParent()) continue;
            if (entry.isTrashItem()) trash.deletePermanently(entry); else trash.put(entry);
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

    public void transfer(@NonNull List<Entry> selected, @NonNull Location destination, boolean move)
            throws FileOperationException {
        if (!canWrite(destination)) throw new FileOperationException("Destination is read-only");
        for (Entry source : selected) {
            if (source.isParent()) continue;
            String name = uniqueName(destination, source.name());
            if (destination.isArchiveEntry()) {
                if (source.isArchiveEntry()) throw new FileOperationException("Archive to archive transfer is unavailable");
                archive.importFromStorage(destination, source, name, false);
                if (move) local.deletePermanently(new File(source.localPath()));
            } else if (source.isArchiveEntry()) {
                archive.extractToStorage(source, new File(destination.physicalPath(), name).getAbsolutePath());
                if (move) archive.delete(List.of(source));
            } else {
                File target = new File(destination.physicalPath(), name);
                if (move) local.move(new File(source.localPath()), target);
                else local.copy(new File(source.localPath()), target);
            }
        }
    }

    public String materializeIfRequired(@NonNull Entry entry) throws FileOperationException {
        return entry.isArchiveEntry() ? archive.materialize(entry) : entry.localPath();
    }

    private String uniqueName(Location destination, String name) throws FileOperationException {
        if (destination.isArchiveEntry()) {
            if (!archive.exists(destination, name)) return name;
        } else if (!new File(destination.physicalPath(), name).exists()) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String suffix = dot > 0 ? name.substring(dot) : "";
        int index = 1;
        String candidate;
        do {
            candidate = stem + " (" + index++ + ")" + suffix;
        } while (destination.isArchiveEntry() ? archive.exists(destination, candidate)
                : new File(destination.physicalPath(), candidate).exists());
        return candidate;
    }

    private static void validName(String value) throws FileOperationException {
        if (value == null || value.isBlank() || value.contains("/") || value.contains("\\")) {
            throw new FileOperationException("Invalid name");
        }
    }
}
