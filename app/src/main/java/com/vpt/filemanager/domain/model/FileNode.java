package com.vpt.filemanager.domain.model;

import java.util.List;

public abstract class FileNode {
    public abstract FilePath path();

    public abstract String name();

    public abstract boolean isDirectory();

    public abstract boolean isSymbolicLink();

    public abstract long sizeBytes();

    public abstract long lastModifiedMillis();

    public abstract FileMetadata metadata();

    public List<FileNode> children() {
        throw new UnsupportedOperationException("Not a directory");
    }

    public FileNode findChild(String name) {
        for (FileNode child : children()) {
            if (child.name().equals(name)) {
                return child;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FileNode && path().equals(((FileNode) o).path());
    }

    @Override
    public int hashCode() {
        return path().hashCode();
    }
}

