package com.vpt.filemanager.domain.usecase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import com.vpt.filemanager.core.concurrent.CancellationSignal;
import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.repository.FileRepository;

public final class SearchFilesUseCase {
    private final FileRepository fileRepository;

    @Inject
    public SearchFilesUseCase(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    public List<FileNode> execute(FilePath root, String query, CancellationSignal cancel)
            throws FileSystemException {
        List<FileNode> out = new ArrayList<>();
        walk(root, query.toLowerCase(Locale.US), cancel, out);
        return out;
    }

    private void walk(FilePath dir, String query, CancellationSignal cancel, List<FileNode> out)
            throws FileSystemException {
        cancel.throwIfCancelled();
        for (FileNode node : fileRepository.list(dir)) {
            if (node.name().toLowerCase(Locale.US).contains(query)) {
                out.add(node);
            }
            if (node.isDirectory()) {
                walk(node.path(), query, cancel, out);
            }
        }
    }
}
