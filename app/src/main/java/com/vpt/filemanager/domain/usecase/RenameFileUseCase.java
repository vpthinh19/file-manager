package com.vpt.filemanager.domain.usecase;

import java.util.concurrent.Future;

import javax.inject.Inject;

import com.vpt.filemanager.core.concurrent.AppExecutors;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.model.Result;
import com.vpt.filemanager.domain.repository.FileRepository;

public final class RenameFileUseCase {
    private final FileRepository fileRepository;
    private final AppExecutors executors;

    @Inject
    public RenameFileUseCase(FileRepository fileRepository, AppExecutors executors) {
        this.fileRepository = fileRepository;
        this.executors = executors;
    }

    public Future<Result<Void>> execute(FilePath src, String newName) {
        return executors.io().submit(() -> {
            try {
                fileRepository.rename(src, src.parent().child(newName));
                return Result.success(null);
            } catch (Throwable e) {
                return Result.failure(e);
            }
        });
    }
}

