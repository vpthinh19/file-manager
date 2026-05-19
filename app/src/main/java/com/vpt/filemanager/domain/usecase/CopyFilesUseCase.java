package com.vpt.filemanager.domain.usecase;

import java.util.List;

import javax.inject.Inject;

import com.vpt.filemanager.core.concurrent.CancellationSignal;
import com.vpt.filemanager.core.concurrent.ProgressReporter;
import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.model.ConflictPolicy;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.repository.FileRepository;

/**
 * Copy a batch of sources into a destination directory under the given conflict policy. Long-
 * running and cancellable: the caller passes a {@link CancellationSignal} that the repository
 * checks between files / chunks, and a {@link ProgressReporter} for incremental UI updates.
 */
public final class CopyFilesUseCase {
    private final FileRepository fileRepository;

    @Inject
    public CopyFilesUseCase(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    public void execute(
            List<FilePath> sources,
            FilePath dstDir,
            ConflictPolicy policy,
            ProgressReporter progress,
            CancellationSignal cancel) throws FileSystemException {
        fileRepository.copyAll(sources, dstDir, policy, progress, cancel);
    }
}
