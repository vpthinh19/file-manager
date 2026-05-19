package com.vpt.filemanager.domain.usecase;

import java.util.List;

import javax.inject.Inject;

import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.repository.FileRepository;

/**
 * List the children of a directory. Synchronous + throwing: the caller decides which thread to run
 * on. ViewModels typically wrap this in {@code AppExecutors.io().submit(...)}; tests can call
 * directly without dragging an executor in.
 */
public final class ListDirectoryUseCase {
    private final FileRepository fileRepository;

    @Inject
    public ListDirectoryUseCase(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    public List<FileNode> execute(FilePath dir) throws FileSystemException {
        return fileRepository.list(dir);
    }
}
