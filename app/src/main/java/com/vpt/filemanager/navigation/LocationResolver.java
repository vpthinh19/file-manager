package com.vpt.filemanager.navigation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vpt.filemanager.content.ContentType;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.entry.Entry;
import com.vpt.filemanager.storage.LocalStorageAdapter;
import com.vpt.filemanager.storage.archive.ArchiveAccess;
import com.vpt.filemanager.storage.bookmarks.BookmarkCollection;
import com.vpt.filemanager.storage.trash.TrashCollection;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Opens exactly one location. Physical storage is read through {@link LocalStorageAdapter};
 * archive locations are delegated to {@link ArchiveAccess}.
 */
@Singleton
public final class LocationResolver {
    private static final int MAX_RESULTS = 1000;
    private static final int MAX_DIRECTORIES = 10000;
    private final LocalStorageAdapter storage;
    private final ArchiveAccess archives;
    private final BookmarkCollection bookmarks;
    private final TrashCollection trash;
    private final ContentDetector content;

    @Inject
    public LocationResolver(LocalStorageAdapter storage, ArchiveAccess archives,
                            BookmarkCollection bookmarks, TrashCollection trash,
                            ContentDetector content) {
        this.storage = storage;
        this.archives = archives;
        this.bookmarks = bookmarks;
        this.trash = trash;
        this.content = content;
    }

    @NonNull
    public NavigationResult open(@NonNull Location location) throws FileOperationException {
        if (location.isTrash()) return new NavigationResult.Entries(trash.list());
        if (location.isBookmarks()) return new NavigationResult.Entries(bookmarks.list());
        if (location.isSearch()) return new NavigationResult.Entries(search(location));
        if (location.isArchiveEntry()) return openArchiveLocation(location);
        File file = storage.resolve(location);
        if (file.isDirectory()) return new NavigationResult.Entries(listDirectory(location, file));
        if (!isCompoundDocument(file) && content.isArchive(file)) {
            return new NavigationResult.Redirect(Location.archive(storage.locationOf(file).storagePath(), "/"));
        }
        return openContent(location, file, false, null);
    }

    private NavigationResult openArchiveLocation(Location location) throws FileOperationException {
        if (archives.isDirectory(location)) {
            List<Entry> entries = new ArrayList<>();
            Location parent = location.parent();
            if (parent != null) entries.add(Entry.parent(parent));
            entries.addAll(archives.list(location));
            return new NavigationResult.Entries(entries);
        }
        String path = location.archiveInnerPath();
        int slash = path.lastIndexOf('/');
        String name = slash < 0 ? path : path.substring(slash + 1);
        File extracted = new File(archives.materialize(Entry.archive(location, name, false, 0L, 0L)));
        return openContent(location, extracted, !archives.canWrite(location), location);
    }

    private List<Entry> listDirectory(Location location, File directory) throws FileOperationException {
        List<Entry> entries = new ArrayList<>();
        Location parent = location.parent();
        if (parent != null) entries.add(Entry.parent(parent));
        for (File child : storage.children(directory)) entries.add(fromFile(child));
        return entries;
    }

    private NavigationResult.OpenContent openContent(Location source, File file, boolean readOnly,
                                                       @Nullable Location archiveEntry)
            throws FileOperationException {
        ContentType type = content.detect(file);
        return new NavigationResult.OpenContent(source, file.getAbsolutePath(), file.getName(),
                type, type != ContentType.TEXT || readOnly, archiveEntry);
    }

    private List<Entry> search(Location location) throws FileOperationException {
        String query = location.query().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return List.of();
        ArrayDeque<File> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        List<Entry> found = new ArrayList<>();
        pending.add(storage.fileAtStoragePath(location.storagePath()));
        while (!pending.isEmpty() && visited.size() < MAX_DIRECTORIES && found.size() < MAX_RESULTS) {
            if (Thread.currentThread().isInterrupted()) throw new FileOperationException("Search cancelled");
            File directory = pending.removeFirst();
            if (!visited.add(directory.getAbsolutePath())) continue;
            for (File child : storage.children(directory)) {
                if (child.getName().toLowerCase(Locale.ROOT).contains(query)) found.add(fromFile(child));
                if (child.isDirectory()) pending.addLast(child);
                if (found.size() >= MAX_RESULTS) break;
            }
        }
        return found;
    }

    private Entry fromFile(File file) throws FileOperationException {
        return Entry.local(storage.locationOf(file), file.getAbsolutePath(), file.getName(),
                file.isDirectory(), file.isDirectory() ? -1L : file.length(), file.lastModified());
    }

    private static boolean isCompoundDocument(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".docx") || name.endsWith(".xlsx") || name.endsWith(".pptx")
                || name.endsWith(".odt") || name.endsWith(".ods") || name.endsWith(".odp")
                || name.endsWith(".epub");
    }
}
