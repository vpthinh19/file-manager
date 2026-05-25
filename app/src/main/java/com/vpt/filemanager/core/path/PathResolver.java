package com.vpt.filemanager.core.path;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vpt.filemanager.content.ContentDetector;
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
 * Opens exactly one path. Physical storage is read through {@link LocalStorageAdapter};
 * archive paths are delegated to {@link ArchiveAccess}.
 *
 * <p>This class will be rewritten in Phase 4 once the Storage and Handler abstractions
 * land. For now it just consumes the new {@link Path} type.
 */
@Singleton
public final class PathResolver {
    private static final int MAX_RESULTS = 1000;
    private static final int MAX_DIRECTORIES = 10000;
    private final LocalStorageAdapter storage;
    private final ArchiveAccess archives;
    private final BookmarkCollection bookmarks;
    private final TrashCollection trash;
    private final ContentDetector content;

    @Inject
    public PathResolver(LocalStorageAdapter storage, ArchiveAccess archives,
                        BookmarkCollection bookmarks, TrashCollection trash,
                        ContentDetector content) {
        this.storage = storage;
        this.archives = archives;
        this.bookmarks = bookmarks;
        this.trash = trash;
        this.content = content;
    }

    @NonNull
    public NavigationResult open(@NonNull Path path) throws FileOperationException {
        if (path.isTrash()) return new NavigationResult.Entries(trash.list());
        if (path.isBookmarks()) return new NavigationResult.Entries(bookmarks.list());
        if (path.isSearch()) return new NavigationResult.Entries(search(path));
        if (path.isInsideArchive()) return openArchivePath(path);
        File file = storage.resolve(path);
        if (file.isDirectory()) return new NavigationResult.Entries(listDirectory(path, file));
        if (!isCompoundDocument(file) && content.isArchive(file)) {
            return new NavigationResult.Redirect(Path.archive(storage.pathOf(file).storagePath(), "/"));
        }
        return openContent(path, file, false, null);
    }

    private NavigationResult openArchivePath(Path path) throws FileOperationException {
        if (archives.isDirectory(path)) {
            List<Entry> entries = new ArrayList<>();
            Path parent = path.parent();
            if (parent != null) entries.add(Entry.parent(parent));
            entries.addAll(archives.list(path));
            return new NavigationResult.Entries(entries);
        }
        String inner = path.archiveInnerPath();
        int slash = inner.lastIndexOf('/');
        String name = slash < 0 ? inner : inner.substring(slash + 1);
        File extracted = new File(archives.materialize(Entry.archive(path, name, false, 0L, 0L)));
        return openContent(path, extracted, !archives.canWrite(path), path);
    }

    private List<Entry> listDirectory(Path path, File directory) throws FileOperationException {
        List<Entry> entries = new ArrayList<>();
        Path parent = path.parent();
        if (parent != null) entries.add(Entry.parent(parent));
        for (File child : storage.children(directory)) entries.add(fromFile(child));
        return entries;
    }

    private NavigationResult.OpenContent openContent(Path source, File file, boolean readOnly,
                                                     @Nullable Path archiveEntry)
            throws FileOperationException {
        ContentType type = content.detect(file);
        return new NavigationResult.OpenContent(source, file.getAbsolutePath(), file.getName(),
                type, type != ContentType.TEXT || readOnly, archiveEntry);
    }

    private List<Entry> search(Path path) throws FileOperationException {
        String query = path.query().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return List.of();
        ArrayDeque<File> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        List<Entry> found = new ArrayList<>();
        pending.add(storage.fileAtStoragePath(path.storagePath()));
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
        return Entry.local(storage.pathOf(file), file.getAbsolutePath(), file.getName(),
                file.isDirectory(), file.isDirectory() ? -1L : file.length(), file.lastModified());
    }

    private static boolean isCompoundDocument(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".docx") || name.endsWith(".xlsx") || name.endsWith(".pptx")
                || name.endsWith(".odt") || name.endsWith(".ods") || name.endsWith(".odp")
                || name.endsWith(".epub");
    }
}
