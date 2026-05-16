package com.vpt.filemanager.domain.usecase;

import javax.inject.Inject;

import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.repository.TrashRepository;

public final class RestoreFromTrashUseCase {
    private final TrashRepository trashRepository;

    @Inject
    public RestoreFromTrashUseCase(TrashRepository trashRepository) {
        this.trashRepository = trashRepository;
    }

    public void execute(String entryId) throws FileSystemException {
        trashRepository.restore(entryId);
    }
}

