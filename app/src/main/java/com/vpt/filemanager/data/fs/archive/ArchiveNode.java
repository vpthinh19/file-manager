package com.vpt.filemanager.data.fs.archive;

import com.vpt.filemanager.domain.model.FileMetadata;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;

public final class ArchiveNode extends FileNode {
    private final FilePath path;
    private final boolean directory;
    private final long sizeBytes;
    private final long lastModifiedMillis;

    public ArchiveNode(FilePath path, boolean directory, long sizeBytes, long lastModifiedMillis) {
        this.path = path;
        this.directory = directory;
        this.sizeBytes = sizeBytes;
        this.lastModifiedMillis = lastModifiedMillis;
    }

    @Override
    public FilePath path() {
        return path;
    }

    @Override
    public String name() {
        return path.name();
    }

    @Override
    public boolean isDirectory() {
        return directory;
    }

    @Override
    public boolean isSymbolicLink() {
        return false;
    }

    @Override
    public long sizeBytes() {
        return sizeBytes;
    }

    @Override
    public long lastModifiedMillis() {
        return lastModifiedMillis;
    }

    @Override
    public FileMetadata metadata() {
        return FileMetadata.builder().sizeBytes(sizeBytes).lastModifiedMillis(lastModifiedMillis).build();
    }
}

