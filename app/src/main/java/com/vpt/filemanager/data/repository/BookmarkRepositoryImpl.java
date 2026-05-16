package com.vpt.filemanager.data.repository;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.repository.BookmarkRepository;

@Singleton
public final class BookmarkRepositoryImpl implements BookmarkRepository {
    private final List<FilePath> bookmarks = new ArrayList<>();

    @Inject
    public BookmarkRepositoryImpl() {
    }

    @Override
    public List<FilePath> bookmarks() {
        return List.copyOf(bookmarks);
    }

    @Override
    public void add(FilePath path) {
        if (!bookmarks.contains(path)) {
            bookmarks.add(path);
        }
    }

    @Override
    public void remove(FilePath path) {
        bookmarks.remove(path);
    }
}

