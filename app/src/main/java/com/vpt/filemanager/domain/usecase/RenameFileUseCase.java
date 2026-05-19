package com.vpt.filemanager.domain.usecase;

import javax.inject.Inject;

import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.repository.FileRepository;

public final class RenameFileUseCase {
    private final FileRepository fileRepository;

    @Inject
    public RenameFileUseCase(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    public void execute(FilePath src, String newName) throws FileSystemException {
        fileRepository.rename(src, src.parent().child(newName));
    }
}
