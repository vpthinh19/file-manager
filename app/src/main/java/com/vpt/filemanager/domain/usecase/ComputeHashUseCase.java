package com.vpt.filemanager.domain.usecase;

import javax.inject.Inject;

import com.vpt.filemanager.core.concurrent.CancellationSignal;
import com.vpt.filemanager.core.concurrent.ProgressReporter;
import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.model.HashAlgorithm;
import com.vpt.filemanager.domain.repository.HashRepository;

public final class ComputeHashUseCase {
    private final HashRepository hashRepository;

    @Inject
    public ComputeHashUseCase(HashRepository hashRepository) {
        this.hashRepository = hashRepository;
    }

    public String execute(
            FilePath path,
            HashAlgorithm algorithm,
            ProgressReporter progress,
            CancellationSignal cancel) throws FileSystemException {
        return hashRepository.compute(path, algorithm, progress, cancel);
    }
}
