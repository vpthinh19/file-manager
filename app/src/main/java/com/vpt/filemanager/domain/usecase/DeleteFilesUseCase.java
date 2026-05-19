package com.vpt.filemanager.domain.usecase;

import java.util.List;

import javax.inject.Inject;

import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.repository.FileRepository;

public final class DeleteFilesUseCase {
    private final FileRepository fileRepository;

    @Inject
    public DeleteFilesUseCase(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    public void execute(List<FilePath> paths, boolean permanent) throws FileSystemException {
        for (FilePath path : paths) {
            fileRepository.delete(path, permanent);
        }
    }
}
