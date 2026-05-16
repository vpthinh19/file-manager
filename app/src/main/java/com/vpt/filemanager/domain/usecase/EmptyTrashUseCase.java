package com.vpt.filemanager.domain.usecase;

import javax.inject.Inject;

import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.repository.TrashRepository;

public final class EmptyTrashUseCase {
    private final TrashRepository trashRepository;

    @Inject
    public EmptyTrashUseCase(TrashRepository trashRepository) {
        this.trashRepository = trashRepository;
    }

    public void execute() throws FileSystemException {
        trashRepository.empty();
    }
}

