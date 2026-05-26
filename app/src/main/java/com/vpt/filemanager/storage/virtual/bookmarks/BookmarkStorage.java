package com.vpt.filemanager.storage.virtual.bookmarks;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.storage.virtual.Storage;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/** {@link Storage} for the virtual Bookmarks collection. Wraps {@link BookmarkCollection}. */
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

    @Override
    public boolean isContainer(@NonNull Path path) {
        return true;
    }

    @NonNull
    @Override
    public List<Entry> list(@NonNull Path path) {
        return bookmarks.list();
    }

    @NonNull
    @Override
    public File materialize(@NonNull Path path) throws FileOperationException {
        throw new FileOperationException("Bookmarks is a collection, not a file");
    }

    @Override
    public boolean canWrite(@NonNull Path path) {
        return false;
    }

    @Override
    public void create(@NonNull Path parent, @NonNull String name, boolean folder)
            throws FileOperationException {
        throw new FileOperationException("Cannot create entries inside bookmarks");
    }

    @Override
    public void rename(@NonNull Entry entry, @NonNull String newName) throws FileOperationException {
        throw new FileOperationException("Cannot rename bookmark entries");
    }

    @Override
    public void delete(@NonNull List<Entry> entries) {
        for (Entry entry : entries) bookmarks.remove(entry);
    }

    @Override
    public void copyInternal(@NonNull Entry source, @NonNull Path destinationParent,
                             @NonNull String name, boolean replace) throws FileOperationException {
        throw new FileOperationException("Cannot copy within bookmarks");
    }

    @Override
    public void moveInternal(@NonNull Entry source, @NonNull Path destinationParent,
                             @NonNull String name, boolean replace) throws FileOperationException {
        throw new FileOperationException("Cannot move within bookmarks");
    }

    @NonNull
    @Override
    public InputStream openRead(@NonNull Entry entry) throws FileOperationException {
        throw new FileOperationException("Bookmark entries are addresses, not files");
    }

    @NonNull
    @Override
    public OutputStream openWrite(@NonNull Entry entry) throws FileOperationException {
        throw new FileOperationException("Bookmarks are read-only");
    }
}
