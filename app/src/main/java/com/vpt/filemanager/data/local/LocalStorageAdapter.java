package com.vpt.filemanager.data.local;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.browser.action.transfer.CancellationToken;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.item.ItemFactory;

/** Local path/byte gateway. It exposes no pane, selection, action or UI state. */
@Singleton
public final class LocalStorageAdapter {
    private final ItemFactory items;

    @Inject
    public LocalStorageAdapter(ItemFactory items) {
        this.items = items;
    }

    @NonNull
    public List<Item> list(@NonNull String directory) throws FileOperationException {
        Path path = nio(directory);
        if (!Files.isDirectory(path)) throw new FileOperationException("Not a directory: " + directory);
        List<Item> result = new ArrayList<>(64);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path child : stream) result.add(build(child));
            return result;
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Unable to list: " + directory, error);
        }
    }

    @NonNull
    public Item inspect(@NonNull String path) throws FileOperationException {
        Path local = nio(path);
        if (!Files.exists(local)) throw new FileOperationException("Path not found: " + path);
        return build(local);
    }

    @NonNull
    public Item create(@NonNull String parent, @NonNull String name, boolean folder)
            throws FileOperationException {
        Path target = nio(parent).resolve(name);
        try {
            if (folder) Files.createDirectory(target); else Files.createFile(target);
            return build(target);
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Cannot create: " + name, error);
        }
    }

    public void rename(@NonNull String source, @NonNull String newName) throws FileOperationException {
        Path oldPath = nio(source);
        try {
            movePath(oldPath, oldPath.resolveSibling(newName));
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Cannot rename: " + source, error);
        }
    }

    public void deletePermanently(@NonNull String path) throws FileOperationException {
        try {
            deleteRecursively(nio(path));
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Cannot delete: " + path, error);
        }
    }

    public void copy(@NonNull String source, @NonNull String destination,
                     @NonNull CancellationToken token) throws FileOperationException {
        try {
            copyRecursively(nio(source), nio(destination), token);
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Cannot copy: " + source, error);
        }
    }

    public void move(@NonNull String source, @NonNull String destination,
                     @NonNull CancellationToken token) throws FileOperationException {
        if (token.isCancelled()) return;
        try {
            movePath(nio(source), nio(destination));
        } catch (IOException directFailure) {
            copy(source, destination, token);
            if (!token.isCancelled()) deletePermanently(source);
        }
    }

    @NonNull
    public InputStream openRead(@NonNull String path) throws FileOperationException {
        try {
            return Files.newInputStream(nio(path), StandardOpenOption.READ);
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Unable to read: " + path, error);
        }
    }

    @NonNull
    public OutputStream openWrite(@NonNull String path) throws FileOperationException {
        try {
            return Files.newOutputStream(nio(path), StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Unable to write: " + path, error);
        }
    }

    public boolean exists(@NonNull String path) {
        return Files.exists(nio(path));
    }

    private Item build(Path path) {
        boolean folder;
        long size;
        long modified;
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            folder = attributes.isDirectory();
            size = folder ? -1L : attributes.size();
            modified = attributes.lastModifiedTime().toMillis();
        } catch (IOException | SecurityException error) {
            File file = path.toFile();
            folder = file.isDirectory();
            size = folder ? -1L : file.length();
            modified = file.lastModified();
        }
        String normalized = path.toString().replace('\\', '/');
        String name = path.getFileName() == null ? normalized : path.getFileName().toString();
        return items.local(normalized, name, folder, size, modified);
    }

    private static void copyRecursively(Path source, Path destination, CancellationToken token)
            throws IOException {
        if (token.isCancelled() || Thread.currentThread().isInterrupted()) {
            token.cancel();
            return;
        }
        if (Files.isDirectory(source) && !Files.isSymbolicLink(source)) {
            Files.createDirectory(destination);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
                for (Path child : stream) {
                    copyRecursively(child, destination.resolve(child.getFileName()), token);
                    if (token.isCancelled()) return;
                }
            }
        } else {
            Files.copy(source, destination);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path) && !Files.isSymbolicLink(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) deleteRecursively(child);
            }
        }
        Files.deleteIfExists(path);
    }

    private static void movePath(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(source, destination);
        }
    }

    private static Path nio(String raw) {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")
                && raw.matches("^/[A-Za-z]:/.*")) return Paths.get(raw.substring(1));
        return Paths.get(raw);
    }
}
