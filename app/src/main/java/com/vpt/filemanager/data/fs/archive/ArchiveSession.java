package com.vpt.filemanager.data.fs.archive;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.vpt.filemanager.core.error.ArchiveException;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;

public final class ArchiveSession implements AutoCloseable {
    private final File archiveFile;
    private final ZipFile zipFile;

    public ArchiveSession(File archiveFile) throws ArchiveException {
        this.archiveFile = archiveFile;
        try {
            this.zipFile = new ZipFile(archiveFile);
        } catch (IOException e) {
            throw new ArchiveException("Unable to open archive: " + archiveFile, e);
        }
    }

    public List<FileNode> list(FilePath archiveRoot, String innerPath) throws ArchiveException {
        String prefix = normalizeEntryPrefix(innerPath);
        Set<String> directNames = new HashSet<>();
        List<FileNode> nodes = new ArrayList<>();
        zipFile.stream().forEach(entry -> {
            String name = entry.getName();
            if (!name.startsWith(prefix) || name.equals(prefix)) {
                return;
            }
            String remaining = name.substring(prefix.length());
            int slash = remaining.indexOf('/');
            String direct = slash < 0 ? remaining : remaining.substring(0, slash);
            if (direct.isEmpty() || !directNames.add(direct)) {
                return;
            }
            boolean directory = slash >= 0 || entry.isDirectory();
            FilePath path = FilePath.inArchive(archiveRoot, join(innerPath, direct));
            nodes.add(new ArchiveNode(path, directory, directory ? -1 : entry.getSize(), entry.getTime()));
        });
        return nodes;
    }

    public InputStream openEntry(String innerPath) throws ArchiveException {
        ZipEntry entry = zipFile.getEntry(stripLeadingSlash(innerPath));
        if (entry == null || entry.isDirectory()) {
            throw new ArchiveException("Archive entry not found: " + innerPath);
        }
        try {
            return zipFile.getInputStream(entry);
        } catch (IOException e) {
            throw new ArchiveException("Unable to open archive entry: " + innerPath, e);
        }
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }

    private static String normalizeEntryPrefix(String innerPath) {
        String stripped = stripLeadingSlash(innerPath);
        if (stripped.isEmpty()) {
            return "";
        }
        return stripped.endsWith("/") ? stripped : stripped + "/";
    }

    private static String stripLeadingSlash(String value) {
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private static String join(String parent, String child) {
        if ("/".equals(parent)) {
            return "/" + child;
        }
        return parent + "/" + child;
    }
}

