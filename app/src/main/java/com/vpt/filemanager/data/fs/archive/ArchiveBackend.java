package com.vpt.filemanager.data.fs.archive;

public interface ArchiveBackend {
    boolean canRead(String mimeOrExtension);

    boolean canWrite(String mimeOrExtension);
}

