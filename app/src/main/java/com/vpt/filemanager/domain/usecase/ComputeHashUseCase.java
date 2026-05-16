package com.vpt.filemanager.domain.usecase;

import java.util.concurrent.Future;

import javax.inject.Inject;

import com.vpt.filemanager.core.concurrent.AppExecutors;
import com.vpt.filemanager.core.concurrent.CancellationSignal;
import com.vpt.filemanager.core.concurrent.ProgressReporter;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.model.HashAlgorithm;
import com.vpt.filemanager.domain.model.Result;
import com.vpt.filemanager.domain.repository.HashRepository;

public final class ComputeHashUseCase {
    private final HashRepository hashRepository;
    private final AppExecutors executors;

    @Inject
    public ComputeHashUseCase(HashRepository hashRepository, AppExecutors executors) {
        this.hashRepository = hashRepository;
        this.executors = executors;
    }

    public Future<Result<String>> execute(
            FilePath path,
            HashAlgorithm algorithm,
            ProgressReporter progress,
            CancellationSignal cancel) {
        return executors.io().submit(() -> {
            try {
                return Result.success(hashRepository.compute(path, algorithm, progress, cancel));
            } catch (Throwable e) {
                return Result.failure(e);
            }
        });
    }
}

