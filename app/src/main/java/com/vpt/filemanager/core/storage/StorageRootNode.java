package com.vpt.filemanager.core.storage;

import com.vpt.filemanager.domain.model.FileMetadata;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;

public final class StorageRootNode extends FileNode {
    private final StorageRoot root;
    private final long totalBytes;
    private final long freeBytes;

    public StorageRootNode(StorageRoot root, long totalBytes, long freeBytes) {
        this.root = root;
        this.totalBytes = totalBytes;
        this.freeBytes = freeBytes;
    }

    public StorageRoot root() {
        return root;
    }

    public long totalBytes() {
        return totalBytes;
    }

    public long freeBytes() {
        return freeBytes;
    }

    @Override
    public FilePath path() {
        return root.path();
    }

    @Override
    public String name() {
        return root.displayName();
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public boolean isSymbolicLink() {
        return false;
    }

    @Override
    public long sizeBytes() {
        return -1;
    }

    @Override
    public long lastModifiedMillis() {
        return -1;
    }

    @Override
    public FileMetadata metadata() {
        return FileMetadata.builder().build();
    }
}
