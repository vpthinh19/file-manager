package com.vpt.filemanager.domain.usecase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;

import javax.inject.Inject;

import com.vpt.filemanager.core.concurrent.AppExecutors;
import com.vpt.filemanager.core.concurrent.CancellationSignal;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.model.Result;
import com.vpt.filemanager.domain.repository.FileRepository;

public final class SearchFilesUseCase {
    private final FileRepository fileRepository;
    private final AppExecutors executors;

    @Inject
    public SearchFilesUseCase(FileRepository fileRepository, AppExecutors executors) {
        this.fileRepository = fileRepository;
        this.executors = executors;
    }

    public Future<Result<List<FileNode>>> execute(FilePath root, String query, CancellationSignal cancel) {
        return executors.io().submit(() -> {
            try {
                List<FileNode> out = new ArrayList<>();
                search(root, query.toLowerCase(Locale.US), cancel, out);
                return Result.success(out);
            } catch (Throwable e) {
                return Result.failure(e);
            }
        });
    }

    private void search(FilePath dir, String query, CancellationSignal cancel, List<FileNode> out) throws Exception {
        cancel.throwIfCancelled();
        for (FileNode node : fileRepository.list(dir)) {
            if (node.name().toLowerCase(Locale.US).contains(query)) {
                out.add(node);
            }
            if (node.isDirectory()) {
                search(node.path(), query, cancel, out);
            }
        }
    }
}

