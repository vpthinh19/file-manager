package com.vpt.filemanager.domain.model.operation;

import com.vpt.filemanager.core.error.FileSystemException;

public interface FileOperation {
    OperationResult execute() throws FileSystemException;

    void cancel();

    OperationProgress progress();
}

