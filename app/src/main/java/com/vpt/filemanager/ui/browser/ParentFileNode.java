package com.vpt.filemanager.ui.browser;

import com.vpt.filemanager.domain.model.FileMetadata;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;

public final class ParentFileNode extends FileNode {
    private final FilePath path;

    public ParentFileNode(FilePath path) {
        this.path = path;
    }

    @Override
    public FilePath path() {
        return path;
    }

    @Override
    public String name() {
        return "..";
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
