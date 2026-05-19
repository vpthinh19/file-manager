package com.vpt.filemanager.domain.repository;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;

/**
 * File-system access for the domain layer. Implementations are scheme-aware (local vs archive)
 * and route through {@code FileSystemRegistry} based on the {@link FilePath#scheme()}.
 *
 * <p>Methods throw {@link FileSystemException} for any failure — callers are expected to wrap
 * calls in their own io scheduling (typically via {@code AppExecutors.io().submit(...)} inside a
 * ViewModel).
 *
 * <p>Bulk operations (copy/move with progress + cancel) are intentionally NOT on this interface in
 * v1. They will return as a separate {@code BulkOpsRepository} when Phase 2C-6 wires the actual
 * batch UI — bundling them on the basic FileRepository forced consumers to pull in
 * progress/cancel/ConflictPolicy types they never used.
 */
public interface FileRepository {
    FileNode resolve(FilePath path) throws FileSystemException;

    List<FileNode> list(FilePath dir) throws FileSystemException;

    InputStream openRead(FilePath path) throws FileSystemException;

    OutputStream openWrite(FilePath path, boolean append) throws FileSystemException;

    FileNode createFile(FilePath path) throws FileSystemException;

    FileNode createDirectory(FilePath path) throws FileSystemException;

    void rename(FilePath src, FilePath dst) throws FileSystemException;

    void delete(FilePath path, boolean permanent) throws FileSystemException;
}
