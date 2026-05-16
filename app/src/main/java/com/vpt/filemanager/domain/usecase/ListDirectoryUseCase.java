package com.vpt.filemanager.domain.usecase;

import java.util.List;
import java.util.concurrent.Future;

import javax.inject.Inject;

import com.vpt.filemanager.core.concurrent.AppExecutors;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.model.Result;
import com.vpt.filemanager.domain.repository.FileRepository;

public final class ListDirectoryUseCase {
    private final FileRepository fileRepository;
    private final AppExecutors executors;

    @Inject
    public ListDirectoryUseCase(FileRepository fileRepository, AppExecutors executors) {
        this.fileRepository = fileRepository;
        this.executors = executors;
    }

    public Future<Result<List<FileNode>>> execute(FilePath dir) {
        return executors.io().submit(() -> {
            try {
                return Result.success(fileRepository.list(dir));
            } catch (Throwable e) {
                return Result.failure(e);
            }
        });
    }
}

