package com.vpt.filemanager.domain.usecase;

import java.util.List;
import java.util.concurrent.Future;

import javax.inject.Inject;

import com.vpt.filemanager.core.concurrent.AppExecutors;
import com.vpt.filemanager.core.concurrent.CancellationSignal;
import com.vpt.filemanager.core.concurrent.ProgressReporter;
import com.vpt.filemanager.domain.model.ConflictPolicy;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.model.Result;
import com.vpt.filemanager.domain.repository.FileRepository;

public final class CopyFilesUseCase {
    private final FileRepository fileRepository;
    private final AppExecutors executors;

    @Inject
    public CopyFilesUseCase(FileRepository fileRepository, AppExecutors executors) {
        this.fileRepository = fileRepository;
        this.executors = executors;
    }

    public Future<Result<Void>> execute(
            List<FilePath> sources,
            FilePath dstDir,
            ConflictPolicy policy,
            ProgressReporter progress,
            CancellationSignal cancel) {
        return executors.io().submit(() -> {
            try {
                fileRepository.copyAll(sources, dstDir, policy, progress, cancel);
                return Result.success(null);
            } catch (Throwable e) {
                return Result.failure(e);
            }
        });
    }
}

