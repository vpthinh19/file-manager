package com.vpt.filemanager.data.fs.archive;

public final class SevenZBackend implements ArchiveBackend {
    @Override
    public boolean canRead(String mimeOrExtension) {
        return "7z".equalsIgnoreCase(mimeOrExtension);
    }

    @Override
    public boolean canWrite(String mimeOrExtension) {
        return false;
    }
}

