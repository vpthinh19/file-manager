package com.vpt.filemanager.domain.usecase;

import java.util.List;
import java.util.concurrent.Future;

import javax.inject.Inject;

import com.vpt.filemanager.core.concurrent.AppExecutors;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.model.Result;
import com.vpt.filemanager.domain.repository.FileRepository;

public final class MoveFilesUseCase {
    private final FileRepository fileRepository;
    private final AppExecutors executors;

    @Inject
    public MoveFilesUseCase(FileRepository fileRepository, AppExecutors executors) {
        this.fileRepository = fileRepository;
        this.executors = executors;
    }

    public Future<Result<Void>> execute(List<FilePath> sources, FilePath dstDir) {
        return executors.io().submit(() -> {
            try {
                for (FilePath source : sources) {
                    fileRepository.rename(source, dstDir.child(source.name()));
                }
                return Result.success(null);
            } catch (Throwable e) {
                return Result.failure(e);
            }
        });
    }
}

