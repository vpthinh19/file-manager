package com.vpt.filemanager.domain.repository;

import java.util.List;

import com.vpt.filemanager.domain.model.FilePath;

public interface BookmarkRepository {
    List<FilePath> bookmarks();

    void add(FilePath path);

    void remove(FilePath path);
}

