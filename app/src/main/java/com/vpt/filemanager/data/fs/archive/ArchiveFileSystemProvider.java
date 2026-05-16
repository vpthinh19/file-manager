package com.vpt.filemanager.data.fs.archive;

import java.io.Closeable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.core.error.ArchiveException;
import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.data.fs.DeleteOptions;
import com.vpt.filemanager.data.fs.FileSystemProvider;
import com.vpt.filemanager.data.fs.ListOptions;
import com.vpt.filemanager.data.fs.WatchListener;
import com.vpt.filemanager.data.fs.WriteMode;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;

@Singleton
public final class ArchiveFileSystemProvider implements FileSystemProvider {
    private final Map<FilePath, ArchiveSession> sessions = new ConcurrentHashMap<>();

    @Inject
    public ArchiveFileSystemProvider() {
    }

    @Override
    public String scheme() {
        return FilePath.SCHEME_ARCHIVE;
    }

    @Override
    public FileNode resolve(FilePath path) throws FileSystemException {
        if ("/".equals(path.path())) {
            return new ArchiveNode(path, true, -1, -1);
        }
        for (FileNode node : list(path.parent(), ListOptions.DEFAULT)) {
            if (node.path().equals(path)) {
                return node;
            }
        }
        throw new ArchiveException("Archive path not found: " + path);
    }

    @Override
    public List<FileNode> list(FilePath dir, ListOptions opts) throws FileSystemException {
        FilePath archivePath = FilePath.parse(dir.authority());
        return sessionFor(archivePath).list(archivePath, dir.path());
    }

    @Override
    public InputStream openRead(FilePath path) throws FileSystemException {
        return sessionFor(FilePath.parse(path.authority())).openEntry(path.path());
    }

    @Override
    public OutputStream openWrite(FilePath path, WriteMode mode) throws FileSystemException {
        throw new ArchiveException("Archive write support is not implemented yet");
    }

    @Override
    public FileNode createFile(FilePath path) throws FileSystemException {
        throw new ArchiveException("Archive write support is not implemented yet");
    }

    @Override
    public FileNode createDirectory(FilePath path) throws FileSystemException {
        throw new ArchiveException("Archive write support is not implemented yet");
    }

    @Override
    public void rename(FilePath src, FilePath dst) throws FileSystemException {
        throw new ArchiveException("Archive write support is not implemented yet");
    }

    @Override
    public void delete(FilePath path, DeleteOptions opts) throws FileSystemException {
        throw new ArchiveException("Archive write support is not implemented yet");
    }

    @Override
    public boolean isSameVolume(FilePath a, FilePath b) {
        return a.authority().equals(b.authority());
    }

    @Override
    public boolean exists(FilePath path) {
        try {
            resolve(path);
            return true;
        } catch (FileSystemException e) {
            return false;
        }
    }

    @Override
    public boolean supportsWrite() {
        return false;
    }

    @Override
    public long freeSpaceBytes(FilePath path) {
        return -1;
    }

    @Override
    public Closeable watch(FilePath dir, WatchListener listener) {
        return null;
    }

    private ArchiveSession sessionFor(FilePath archivePath) throws ArchiveException {
        ArchiveSession existing = sessions.get(archivePath);
        if (existing != null) {
            return existing;
        }
        ArchiveSession created = new ArchiveSession(new File(archivePath.path()));
        sessions.put(archivePath, created);
        return created;
    }
}

