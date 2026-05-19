package com.vpt.filemanager.data.repository;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.data.fs.DeleteOptions;
import com.vpt.filemanager.data.fs.FileSystemRegistry;
import com.vpt.filemanager.data.fs.ListOptions;
import com.vpt.filemanager.data.fs.WriteMode;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.repository.FileRepository;

/**
 * Default {@link FileRepository} — a thin dispatch layer that picks the right
 * {@link com.vpt.filemanager.data.fs.FileSystemProvider} from
 * {@link FileSystemRegistry} based on the {@link FilePath#scheme()}.
 *
 * <p>v1 scope keeps this strictly single-file: copy/move-with-progress operations have been
 * removed from the interface since no caller was wiring the {@code ProgressReporter} +
 * {@code CancellationSignal} machinery, and the conflict resolution UI handles the batch case
 * differently (per-batch pre-flight dialog rather than per-file callback). When Phase 2C-6 builds
 * the batch UI we'll introduce a dedicated bulk-ops type instead of stuffing it back here.
 */
@Singleton
public final class FileRepositoryImpl implements FileRepository {
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
        return registry.providerFor(path)
                .openWrite(path, append ? WriteMode.APPEND : WriteMode.TRUNCATE);
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
        registry.providerFor(path)
                .delete(path, permanent ? DeleteOptions.PERMANENT : DeleteOptions.TRASH);
    }
}
