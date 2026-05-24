package com.vpt.filemanager.browser.item.source;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.browser.action.browse.SortOrder;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.browser.item.Path;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.data.local.LocalStorageAdapter;
import com.vpt.filemanager.data.archive.ArchiveRepository;
import com.vpt.filemanager.data.persistence.BookmarkStore;
import com.vpt.filemanager.data.persistence.TrashStore;
import com.vpt.filemanager.browser.rule.StorageBoundary;

/** Materializes the item list for one visible path without retaining a virtual tree. */
@Singleton
public final class ItemFetcher {
    private static final int MAX_RESULTS = 1000;
    private static final int MAX_DIRECTORIES = 10000;
    private final LocalStorageAdapter files;
    private final ArchiveRepository archives;
    private final BookmarkStore bookmarks;
    private final TrashStore trash;

    @Inject
    public ItemFetcher(LocalStorageAdapter files, ArchiveRepository archives,
                       BookmarkStore bookmarks, TrashStore trash) {
        this.files = files;
        this.archives = archives;
        this.bookmarks = bookmarks;
        this.trash = trash;
    }

    @NonNull
    public List<Item> fetch(@NonNull Path path) throws FileOperationException {
        if (path.isTrash()) return trash.list();
        if (path.isBookmarks()) return bookmarks.list();
        if (path.isSearch()) return search(path.directory(), path.query());
        List<Item> items = new ArrayList<>(path.isArchive()
                ? archives.list(path) : files.list(path.directory()));
        if (path.isArchive() || StorageBoundary.canNavigateUp(path)) {
            items.add(Item.parent(path.parent()));
        }
        return items;
    }

    @NonNull
    public List<Item> fetch(@NonNull Path path, @NonNull SortOrder order)
            throws FileOperationException {
        List<Item> items = new ArrayList<>(fetch(path));
        items.sort(order.comparator());
        return List.copyOf(items);
    }

    private List<Item> search(String scope, String query) throws FileOperationException {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        if (needle.isEmpty()) return List.of();
        List<Item> result = new ArrayList<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(scope);
        while (!pending.isEmpty() && visited.size() < MAX_DIRECTORIES
                && result.size() < MAX_RESULTS) {
            if (Thread.currentThread().isInterrupted()) {
                throw new FileOperationException("Search cancelled");
            }
            String directory = pending.removeFirst();
            if (!visited.add(directory)) continue;
            List<Item> entries;
            try {
                entries = files.list(directory);
            } catch (FileOperationException error) {
                if (directory.equals(scope)) throw error;
                continue;
            }
            for (Item item : entries) {
                if (item.name().toLowerCase(Locale.ROOT).contains(needle)) result.add(item);
                if (item.isFolder()) pending.addLast(item.localPath());
                if (result.size() >= MAX_RESULTS) break;
            }
        }
        return List.copyOf(result);
    }
}
