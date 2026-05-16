package com.vpt.filemanager.data.fs.archive;

public final class TarBackend implements ArchiveBackend {
    @Override
    public boolean canRead(String mimeOrExtension) {
        return "tar".equalsIgnoreCase(mimeOrExtension);
    }

    @Override
    public boolean canWrite(String mimeOrExtension) {
        return canRead(mimeOrExtension);
    }
}

