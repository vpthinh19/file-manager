package com.vpt.filemanager.data.repository;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.repository.FileRepository;
import com.vpt.filemanager.domain.repository.TrashRepository;

@Singleton
public final class TrashRepositoryImpl implements TrashRepository {
    private final FileRepository fileRepository;

    @Inject
    public TrashRepositoryImpl(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    @Override
    public void moveToTrash(FilePath path) throws FileSystemException {
        // v1 scaffold: permanent delete until Room-backed per-storage trash metadata is wired.
        fileRepository.delete(path, true);
    }

    @Override
    public void restore(String entryId) throws FileSystemException {
        throw new FileSystemException("Trash restore is not implemented yet: " + entryId);
    }

    @Override
    public void empty() {
    }

    @Override
    public List<FilePath> entries() {
        return Collections.emptyList();
    }
}

