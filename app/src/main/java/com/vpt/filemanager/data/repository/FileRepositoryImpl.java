package com.vpt.filemanager.data.repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.core.concurrent.CancellationSignal;
import com.vpt.filemanager.core.concurrent.ProgressReporter;
import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.data.fs.DeleteOptions;
import com.vpt.filemanager.data.fs.FileSystemProvider;
import com.vpt.filemanager.data.fs.FileSystemRegistry;
import com.vpt.filemanager.data.fs.ListOptions;
import com.vpt.filemanager.data.fs.WriteMode;
import com.vpt.filemanager.domain.model.ConflictPolicy;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.repository.FileRepository;

@Singleton
public final class FileRepositoryImpl implements FileRepository {
    private static final int BUFFER_SIZE = 64 * 1024;

    private final FileSystemRegistry registry;

    @Inject
    public FileRepositoryImpl(FileSystemRegistry registry) {
        this.registry = registry;
    }

    @Override
    public FileNode resolve(FilePath path) throws FileSystemException {
        return registry.providerFor(path).resolve(path);
    }

    @Override
    public List<FileNode> list(FilePath dir) throws FileSystemException {
        return registry.providerFor(dir).list(dir, ListOptions.DEFAULT);
    }

    @Override
    public InputStream openRead(FilePath path) throws FileSystemException {
        return registry.providerFor(path).openRead(path);
    }

    @Override
    public OutputStream openWrite(FilePath path, boolean append) throws FileSystemException {
        return registry.providerFor(path).openWrite(path, append ? WriteMode.APPEND : WriteMode.TRUNCATE);
    }

    @Override
    public FileNode createFile(FilePath path) throws FileSystemException {
        return registry.providerFor(path).createFile(path);
    }

    @Override
    public FileNode createDirectory(FilePath path) throws FileSystemException {
        return registry.providerFor(path).createDirectory(path);
    }

    @Override
    public void rename(FilePath src, FilePath dst) throws FileSystemException {
        registry.providerFor(src).rename(src, dst);
    }

    @Override
    public void delete(FilePath path, boolean permanent) throws FileSystemException {
        registry.providerFor(path).delete(path, permanent ? DeleteOptions.PERMANENT : DeleteOptions.TRASH);
    }

    @Override
    public void copyAll(
            List<FilePath> sources,
            FilePath dstDir,
            ConflictPolicy policy,
            ProgressReporter progress,
            CancellationSignal cancel) throws FileSystemException {
        long totalBytes = estimateSize(sources);
        long copiedBytes = 0;
        for (FilePath source : sources) {
            cancel.throwIfCancelled();
            copiedBytes += copyOne(source, dstDir.child(source.name()), policy, progress, copiedBytes, totalBytes, cancel);
        }
    }

    private long copyOne(
            FilePath source,
            FilePath destination,
            ConflictPolicy policy,
            ProgressReporter progress,
            long baseBytes,
            long totalBytes,
            CancellationSignal cancel) throws FileSystemException {
        FileSystemProvider dstProvider = registry.providerFor(destination);
        if (dstProvider.exists(destination)) {
            if (policy == ConflictPolicy.SKIP) {
                return 0;
            }
            if (policy == ConflictPolicy.RENAME) {
                destination = uniqueDestination(destination);
            } else if (policy == ConflictPolicy.ASK) {
                throw new FileSystemException("Conflict requires UI decision: " + destination);
            }
        }

        FileNode node = resolve(source);
        if (node.isDirectory()) {
            createDirectory(destination);
            long copied = 0;
            for (FileNode child : list(source)) {
                copied += copyOne(child.path(), destination.child(child.name()), policy, progress,
                        baseBytes + copied, totalBytes, cancel);
            }
            return copied;
        }

        long copied = 0;
        try (InputStream in = openRead(source);
             OutputStream out = dstProvider.openWrite(destination, WriteMode.TRUNCATE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                cancel.throwIfCancelled();
                out.write(buffer, 0, read);
                copied += read;
                progress.onProgress(baseBytes + copied, totalBytes, source.name());
            }
        } catch (IOException e) {
            throw new FileSystemException("Copy failed: " + source + " -> " + destination, e);
        }
        return copied;
    }

    private long estimateSize(List<FilePath> sources) {
        long total = 0;
        for (FilePath source : sources) {
            try {
                FileNode node = resolve(source);
                if (node.isDirectory()) {
                    total += estimateSizeFromChildren(source);
                } else if (node.sizeBytes() > 0) {
                    total += node.sizeBytes();
                }
            } catch (FileSystemException ignored) {
                return -1;
            }
        }
        return total;
    }

    private long estimateSizeFromChildren(FilePath dir) throws FileSystemException {
        long total = 0;
        for (FileNode child : list(dir)) {
            if (child.isDirectory()) {
                total += estimateSizeFromChildren(child.path());
            } else if (child.sizeBytes() > 0) {
                total += child.sizeBytes();
            }
        }
        return total;
    }

    private FilePath uniqueDestination(FilePath original) {
        int index = 1;
        FilePath candidate = original;
        while (registry.providerFor(candidate).exists(candidate)) {
            String name = original.name();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";
            candidate = original.parent().child(base + " (" + index + ")" + ext);
            index++;
        }
        return candidate;
    }
}

