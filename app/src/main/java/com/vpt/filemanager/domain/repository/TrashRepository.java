package com.vpt.filemanager.domain.repository;

import java.util.List;

import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.model.FilePath;

public interface TrashRepository {
    void moveToTrash(FilePath path) throws FileSystemException;

    void restore(String entryId) throws FileSystemException;

    void empty() throws FileSystemException;

    List<FilePath> entries() throws FileSystemException;
}

