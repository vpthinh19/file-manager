package com.vpt.filemanager.domain.usecase;

import javax.inject.Inject;

import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.repository.FileRepository;

public final class CreateFolderUseCase {
    private final FileRepository fileRepository;

    @Inject
    public CreateFolderUseCase(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    public FileNode execute(FilePath path) throws FileSystemException {
        return fileRepository.createDirectory(path);
    }
}
