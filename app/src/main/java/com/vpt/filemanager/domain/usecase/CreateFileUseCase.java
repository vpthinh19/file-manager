package com.vpt.filemanager.domain.usecase;

import javax.inject.Inject;

import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.repository.FileRepository;

public final class CreateFileUseCase {
    private final FileRepository fileRepository;

    @Inject
    public CreateFileUseCase(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    public FileNode execute(FilePath path) throws FileSystemException {
        return fileRepository.createFile(path);
    }
}
