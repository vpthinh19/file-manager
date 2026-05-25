package com.vpt.filemanager.resolver;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.handler.ExternalHandler;
import com.vpt.filemanager.handler.FolderHandler;
import com.vpt.filemanager.handler.ImageHandler;
import com.vpt.filemanager.handler.MediaHandler;
import com.vpt.filemanager.handler.TextHandler;
import com.vpt.filemanager.handler.archive.ArchiveHandler;
import com.vpt.filemanager.model.ContentKind;
import com.vpt.filemanager.model.Entry;
import com.vpt.filemanager.model.Location;
import com.vpt.filemanager.storage.LocalStorageAdapter;
import com.vpt.filemanager.storage.index.BookmarkIndex;
import com.vpt.filemanager.storage.index.TrashIndex;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Resolves exactly one pane location into rows or one full-screen content surface. */
@Singleton
public final class EntryResolver {
    private static final int MAX_RESULTS = 1000;
    private static final int MAX_DIRECTORIES = 10000;
    private final LocalStorageAdapter storage;
    private final FolderHandler folders;
    private final ArchiveHandler archives;
    private final BookmarkIndex bookmarks;
    private final TrashIndex trash;
    private final ContentProbe probe;
    private final TextHandler text;
    private final ImageHandler images;
    private final MediaHandler media;
    private final ExternalHandler external;
    private final EntryFactory entries;

    @Inject
    public EntryResolver(LocalStorageAdapter storage, FolderHandler folders, ArchiveHandler archives,
                         BookmarkIndex bookmarks, TrashIndex trash, ContentProbe probe,
                         TextHandler text, ImageHandler images, MediaHandler media,
                         ExternalHandler external, EntryFactory entries) {
        this.storage = storage;
        this.folders = folders;
        this.archives = archives;
        this.bookmarks = bookmarks;
        this.trash = trash;
        this.probe = probe;
        this.text = text;
        this.images = images;
        this.media = media;
        this.external = external;
        this.entries = entries;
    }

    @NonNull
    public ResolveResult resolve(@NonNull Location location) throws FileOperationException {
        if (location.isTrash()) return new ResolveResult.Directory(trash.list());
        if (location.isBookmarks()) return new ResolveResult.Directory(bookmarks.list());
        if (location.isSearch()) return new ResolveResult.Directory(search(location));
        if (location.isArchiveEntry()) return resolveArchiveEntry(location);
        File file = storage.resolve(location);
        if (file.isDirectory()) return folders.open(location, file);
        if (!opensExternallyDespiteContainer(file) && probe.isArchive(file)) return new ResolveResult.ReplaceLocation(
                Location.archive(file.getAbsolutePath(), "/"));
        return openContent(location, file, false, null);
    }

    private ResolveResult resolveArchiveEntry(Location location) throws FileOperationException {
        if (archives.isDirectory(location)) {
            List<Entry> result = new ArrayList<>();
            Location parent = location.parent();
            if (parent != null) result.add(Entry.parent(parent));
            result.addAll(archives.list(location));
            return new ResolveResult.Directory(result);
        }
        String name = location.archiveInnerPath();
        int slash = name.lastIndexOf('/');
        name = slash < 0 ? name : name.substring(slash + 1);
        Entry entry = Entry.archive(location, name, false, 0L, 0L);
        File materialized = new File(archives.materialize(entry));
        return openContent(location, materialized, !archives.canWrite(location), location);
    }

    private ResolveResult openContent(Location source, File file, boolean readOnly,
                                      Location archiveEntry) throws FileOperationException {
        ContentKind kind = probe.detectNonArchive(file);
        return switch (kind) {
            case TEXT -> text.open(source, file, readOnly, archiveEntry);
            case IMAGE -> images.open(source, file, archiveEntry);
            case AUDIO -> media.open(source, file, false, archiveEntry);
            case VIDEO -> media.open(source, file, true, archiveEntry);
            case EXTERNAL -> external.open(source, file, archiveEntry);
        };
    }

    private static boolean opensExternallyDespiteContainer(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".docx") || name.endsWith(".xlsx") || name.endsWith(".pptx")
                || name.endsWith(".odt") || name.endsWith(".ods") || name.endsWith(".odp")
                || name.endsWith(".epub");
    }

    private List<Entry> search(Location location) throws FileOperationException {
        String query = location.query().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return List.of();
        ArrayDeque<File> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        List<Entry> found = new ArrayList<>();
        pending.add(new File(location.physicalPath()));
        while (!pending.isEmpty() && visited.size() < MAX_DIRECTORIES && found.size() < MAX_RESULTS) {
            if (Thread.currentThread().isInterrupted()) throw new FileOperationException("Search cancelled");
            File directory = pending.removeFirst();
            if (!visited.add(directory.getAbsolutePath())) continue;
            for (File child : storage.children(directory)) {
                if (child.getName().toLowerCase(Locale.ROOT).contains(query)) {
                    found.add(entries.physical(child));
                }
                if (child.isDirectory()) pending.addLast(child);
                if (found.size() >= MAX_RESULTS) break;
            }
        }
        return found;
    }
}
