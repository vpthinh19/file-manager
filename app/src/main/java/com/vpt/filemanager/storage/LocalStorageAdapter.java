package com.vpt.filemanager.storage;

import android.os.Environment;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.path.Path;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/** The only adapter performing ordinary physical filesystem reads and mutations. */
@Singleton
public final class LocalStorageAdapter {
    private final File root;

    @Inject
    public LocalStorageAdapter() {
        this(Environment.getExternalStorageDirectory());
    }

    /** Alternate root used by host-side tests. */
    public LocalStorageAdapter(@NonNull File root) {
        this.root = root;
    }

    @NonNull
    public File resolve(@NonNull Path path) throws FileOperationException {
        if (!path.isStorage() || path.isInsideArchive()) {
            throw new FileOperationException("Path does not identify one physical file");
        }
        File file = fileAtStoragePath(path.storagePath());
        if (!file.exists()) throw new FileOperationException("Path not found: " + file);
        return file;
    }

    /** Maps a virtual storage path to its raw physical file. */
    @NonNull
    public File fileAtStoragePath(@NonNull String virtualPath) {
        return virtualPath.isEmpty() ? root : new File(root, virtualPath.substring(1));
    }

    /** Maps a physical child read by this adapter back to its virtual pane path. */
    @NonNull
    public Path pathOf(@NonNull File file) throws FileOperationException {
        String base = root.getAbsolutePath().replace('\\', '/');
        String target = file.getAbsolutePath().replace('\\', '/');
        if (target.equals(base)) return Path.storageRoot();
        if (!target.startsWith(base + "/")) {
            throw new FileOperationException("File is outside storage: " + file);
        }
        return Path.storage(target.substring(base.length()));
    }

    @NonNull
    public File rootDirectory() {
        return root;
    }

    @NonNull
    public List<File> children(@NonNull File directory) throws FileOperationException {
        if (!directory.isDirectory()) throw new FileOperationException("Not a directory: " + directory);
        File[] files = directory.listFiles();
        if (files == null) throw new FileOperationException("Unable to list: " + directory);
        List<File> result = new ArrayList<>(files.length);
        for (File file : files) result.add(file);
        return result;
    }

    @NonNull
    public File create(@NonNull File parent, @NonNull String name, boolean folder)
            throws FileOperationException {
        File target = child(parent, name);
        try {
            boolean created = folder ? target.mkdir() : target.createNewFile();
            if (!created) throw new FileOperationException("Name already exists: " + name);
            return target;
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Cannot create: " + name, error);
        }
    }

    public void rename(@NonNull File source, @NonNull String newName) throws FileOperationException {
        move(source, child(source.getParentFile(), newName));
    }

    public void deletePermanently(@NonNull File source) throws FileOperationException {
        try {
            deleteRecursively(source.toPath());
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Cannot delete: " + source.getName(), error);
        }
    }

    public void copy(@NonNull File source, @NonNull File destination) throws FileOperationException {
        try {
            copyRecursively(source.toPath(), destination.toPath());
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Cannot copy: " + source.getName(), error);
        }
    }

    public void move(@NonNull File source, @NonNull File destination) throws FileOperationException {
        try {
            movePath(source.toPath(), destination.toPath());
        } catch (IOException directFailure) {
            copy(source, destination);
            deletePermanently(source);
        }
    }

    @NonNull
    public InputStream openRead(@NonNull File source) throws FileOperationException {
        try {
            return Files.newInputStream(source.toPath(), StandardOpenOption.READ);
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Unable to read: " + source, error);
        }
    }

    @NonNull
    public OutputStream openWrite(@NonNull File target) throws FileOperationException {
        try {
            return Files.newOutputStream(target.toPath(), StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Unable to write: " + target, error);
        }
    }

    public boolean exists(@NonNull File file) {
        return file.exists();
    }

    private static File child(File parent, String name) throws FileOperationException {
        if (parent == null || name == null || name.isBlank() || name.contains("/") || name.contains("\\")) {
            throw new FileOperationException("Invalid name");
        }
        return new File(parent, name.trim());
    }

    private static void copyRecursively(java.nio.file.Path source, java.nio.file.Path destination) throws IOException {
        if (Files.isDirectory(source) && !Files.isSymbolicLink(source)) {
            Files.createDirectory(destination);
            try (DirectoryStream<java.nio.file.Path> stream = Files.newDirectoryStream(source)) {
                for (java.nio.file.Path child : stream) copyRecursively(child, destination.resolve(child.getFileName()));
            }
        } else {
            Files.copy(source, destination);
        }
    }

    private static void deleteRecursively(java.nio.file.Path path) throws IOException {
        if (Files.isDirectory(path) && !Files.isSymbolicLink(path)) {
            try (DirectoryStream<java.nio.file.Path> stream = Files.newDirectoryStream(path)) {
                for (java.nio.file.Path child : stream) deleteRecursively(child);
            }
        }
        Files.deleteIfExists(path);
    }

    private static void movePath(java.nio.file.Path source, java.nio.file.Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(source, destination);
        }
    }
}
