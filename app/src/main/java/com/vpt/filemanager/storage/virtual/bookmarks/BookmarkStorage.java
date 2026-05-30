package com.vpt.filemanager.storage.virtual.bookmarks;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.storage.virtual.Storage;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Read-only {@link Storage} for the virtual Bookmarks collection. */
@Singleton
public final class BookmarkStorage implements Storage {
    private final BookmarkCollection bookmarks;

    @Inject
    public BookmarkStorage(BookmarkCollection bookmarks) {
        this.bookmarks = bookmarks;
    }

    @Override
    public boolean handles(@NonNull Path path) {
        return path.isBookmarks();
    }

    @NonNull
    @Override
    public List<Entry> list(@NonNull Path path) {
        return bookmarks.list();
    }

    @Override
    public void delete(@NonNull List<Entry> entries) {
        for (Entry entry : entries) bookmarks.remove(entry);
    }
}
