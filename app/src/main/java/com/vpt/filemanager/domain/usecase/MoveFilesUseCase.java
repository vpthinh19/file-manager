package com.vpt.filemanager.domain.usecase;

import java.util.List;

import javax.inject.Inject;

import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.repository.FileRepository;

public final class MoveFilesUseCase {
    private final FileRepository fileRepository;

    @Inject
    public MoveFilesUseCase(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    public void execute(List<FilePath> sources, FilePath dstDir) throws FileSystemException {
        for (FilePath source : sources) {
            fileRepository.rename(source, dstDir.child(source.name()));
        }
    }
}
