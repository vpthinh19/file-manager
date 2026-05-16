package com.vpt.filemanager.domain.usecase;

import java.util.concurrent.Future;

import javax.inject.Inject;

import com.vpt.filemanager.core.concurrent.AppExecutors;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.model.Result;
import com.vpt.filemanager.domain.repository.FileRepository;

public final class CreateFileUseCase {
    private final FileRepository fileRepository;
    private final AppExecutors executors;

    @Inject
    public CreateFileUseCase(FileRepository fileRepository, AppExecutors executors) {
        this.fileRepository = fileRepository;
        this.executors = executors;
    }

    public Future<Result<FileNode>> execute(FilePath path) {
        return executors.io().submit(() -> {
            try {
                return Result.success(fileRepository.createFile(path));
            } catch (Throwable e) {
                return Result.failure(e);
            }
        });
    }
}
