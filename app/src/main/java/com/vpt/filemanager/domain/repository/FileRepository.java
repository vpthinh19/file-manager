package com.vpt.filemanager.domain.repository;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.vpt.filemanager.core.concurrent.CancellationSignal;
import com.vpt.filemanager.core.concurrent.ProgressReporter;
import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.model.ConflictPolicy;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;

public interface FileRepository {
    FileNode resolve(FilePath path) throws FileSystemException;

    List<FileNode> list(FilePath dir) throws FileSystemException;

    InputStream openRead(FilePath path) throws FileSystemException;

    OutputStream openWrite(FilePath path, boolean append) throws FileSystemException;

    FileNode createFile(FilePath path) throws FileSystemException;

    FileNode createDirectory(FilePath path) throws FileSystemException;

    void rename(FilePath src, FilePath dst) throws FileSystemException;

    void delete(FilePath path, boolean permanent) throws FileSystemException;

    void copyAll(
            List<FilePath> sources,
            FilePath dstDir,
            ConflictPolicy policy,
            ProgressReporter progress,
            CancellationSignal cancel) throws FileSystemException;
}

