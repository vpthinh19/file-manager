package com.vpt.filemanager.domain.repository;

import com.vpt.filemanager.core.concurrent.CancellationSignal;
import com.vpt.filemanager.core.concurrent.ProgressReporter;
import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.model.HashAlgorithm;

public interface HashRepository {
    String compute(FilePath path, HashAlgorithm algorithm, ProgressReporter progress, CancellationSignal cancel)
            throws FileSystemException;
}

