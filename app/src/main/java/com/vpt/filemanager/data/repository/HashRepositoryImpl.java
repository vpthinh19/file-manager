package com.vpt.filemanager.data.repository;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.core.concurrent.CancellationSignal;
import com.vpt.filemanager.core.concurrent.ProgressReporter;
import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.model.HashAlgorithm;
import com.vpt.filemanager.domain.repository.FileRepository;
import com.vpt.filemanager.domain.repository.HashRepository;

@Singleton
public final class HashRepositoryImpl implements HashRepository {
    private static final int BUFFER_SIZE = 64 * 1024;

    private final FileRepository fileRepository;

    @Inject
    public HashRepositoryImpl(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    @Override
    public String compute(FilePath path, HashAlgorithm algorithm, ProgressReporter progress, CancellationSignal cancel)
            throws FileSystemException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm.messageDigestName());
            FileNode node = fileRepository.resolve(path);
            long total = node.sizeBytes();
            long done = 0;
            try (InputStream in = fileRepository.openRead(path)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    cancel.throwIfCancelled();
                    digest.update(buffer, 0, read);
                    done += read;
                    progress.onProgress(done, total, algorithm.name());
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new FileSystemException("Unable to compute hash: " + path, e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }
}

