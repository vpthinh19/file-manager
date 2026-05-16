package com.vpt.filemanager.domain.usecase;

import java.util.List;
import java.util.concurrent.Future;

import javax.inject.Inject;

import com.vpt.filemanager.core.concurrent.AppExecutors;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.model.Result;
import com.vpt.filemanager.domain.repository.FileRepository;

public final class DeleteFilesUseCase {
    private final FileRepository fileRepository;
    private final AppExecutors executors;

    @Inject
    public DeleteFilesUseCase(FileRepository fileRepository, AppExecutors executors) {
        this.fileRepository = fileRepository;
        this.executors = executors;
    }

    public Future<Result<Void>> execute(List<FilePath> paths, boolean permanent) {
        return executors.io().submit(() -> {
            try {
                for (FilePath path : paths) {
                    fileRepository.delete(path, permanent);
                }
                return Result.success(null);
            } catch (Throwable e) {
                return Result.failure(e);
            }
        });
    }
}

