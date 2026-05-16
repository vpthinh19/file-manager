package com.vpt.filemanager.data.fs;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;

public interface FileSystemProvider {
    String scheme();

    FileNode resolve(FilePath path) throws FileSystemException;

    List<FileNode> list(FilePath dir, ListOptions opts) throws FileSystemException;

    InputStream openRead(FilePath path) throws FileSystemException;

    OutputStream openWrite(FilePath path, WriteMode mode) throws FileSystemException;

    FileNode createFile(FilePath path) throws FileSystemException;

    FileNode createDirectory(FilePath path) throws FileSystemException;

    void rename(FilePath src, FilePath dst) throws FileSystemException;

    void delete(FilePath path, DeleteOptions opts) throws FileSystemException;

    boolean isSameVolume(FilePath a, FilePath b);

    boolean exists(FilePath path);

    boolean supportsWrite();

    long freeSpaceBytes(FilePath path);

    Closeable watch(FilePath dir, WatchListener listener);
}

