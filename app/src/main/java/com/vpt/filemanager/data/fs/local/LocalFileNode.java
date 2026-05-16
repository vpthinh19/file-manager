package com.vpt.filemanager.data.fs.local;

import java.io.File;

import com.vpt.filemanager.core.util.MimeTypes;
import com.vpt.filemanager.domain.model.FileMetadata;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;

public final class LocalFileNode extends FileNode {
    private final File file;
    private final FilePath path;
    private FileMetadata metadata;

    public LocalFileNode(File file) {
        this.file = file;
        this.path = FilePath.local(file.getAbsolutePath());
    }

    @Override
    public FilePath path() {
        return path;
    }

    @Override
    public String name() {
        String name = file.getName();
        return name.isEmpty() ? file.getAbsolutePath() : name;
    }

    @Override
    public boolean isDirectory() {
        return file.isDirectory();
    }

    @Override
    public boolean isSymbolicLink() {
        try {
            return java.nio.file.Files.isSymbolicLink(file.toPath());
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public long sizeBytes() {
        return isDirectory() ? -1 : file.length();
    }

    @Override
    public long lastModifiedMillis() {
        return file.lastModified();
    }

    @Override
    public FileMetadata metadata() {
        if (metadata == null) {
            metadata = FileMetadata.builder()
                    .sizeBytes(sizeBytes())
                    .lastModifiedMillis(lastModifiedMillis())
                    .mimeType(MimeTypes.detect(name()))
                    .build();
        }
        return metadata;
    }
}

