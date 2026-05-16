package com.vpt.filemanager.data.fs.archive;

import javax.inject.Inject;

public final class ZipBackend implements ArchiveBackend {
    @Inject
    public ZipBackend() {
    }

    @Override
    public boolean canRead(String mimeOrExtension) {
        return "zip".equalsIgnoreCase(mimeOrExtension) || "application/zip".equalsIgnoreCase(mimeOrExtension);
    }

    @Override
    public boolean canWrite(String mimeOrExtension) {
        return canRead(mimeOrExtension);
    }
}

