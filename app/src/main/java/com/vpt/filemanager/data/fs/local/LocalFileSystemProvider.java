package com.vpt.filemanager.data.fs.local;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.data.fs.DeleteOptions;
import com.vpt.filemanager.data.fs.FileSystemProvider;
import com.vpt.filemanager.data.fs.ListOptions;
import com.vpt.filemanager.data.fs.WatchListener;
import com.vpt.filemanager.data.fs.WriteMode;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;

@Singleton
public final class LocalFileSystemProvider implements FileSystemProvider {
    @Inject
    public LocalFileSystemProvider() {
    }

    @Override
    public String scheme() {
        return FilePath.SCHEME_FILE;
    }

    @Override
    public FileNode resolve(FilePath path) throws FileSystemException {
        requireLocal(path);
        File file = new File(path.path());
        if (!file.exists()) {
            throw new FileSystemException("Path not found: " + path.path());
        }
        return new LocalFileNode(file);
    }

    @Override
    public List<FileNode> list(FilePath dir, ListOptions opts) throws FileSystemException {
        requireLocal(dir);
        Path nioPath = toNioPath(dir);
        if (!Files.isDirectory(nioPath)) {
            throw new FileSystemException("Not a directory: " + dir.path());
        }
        List<FileNode> nodes = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(nioPath)) {
            for (Path child : stream) {
                String name = child.getFileName() == null ? child.toString() : child.getFileName().toString();
                if (!opts.showHidden() && name.startsWith(".")) {
                    continue;
                }
                nodes.add(new LocalFileNode(child.toFile()));
            }
        } catch (IOException | SecurityException e) {
            throw new FileSystemException("Unable to list: " + dir.path(), e);
        }
        nodes.sort(Comparator
                .comparing(FileNode::isDirectory).reversed()
                .thenComparing(node -> node.name().toLowerCase()));
        return nodes;
    }

    @Override
    public InputStream openRead(FilePath path) throws FileSystemException {
        requireLocal(path);
        try {
            return Files.newInputStream(toNioPath(path), StandardOpenOption.READ);
        } catch (IOException | SecurityException e) {
            throw new FileSystemException("Unable to open for read: " + path.path(), e);
        }
    }

    @Override
    public OutputStream openWrite(FilePath path, WriteMode mode) throws FileSystemException {
        requireLocal(path);
        try {
            Path nioPath = toNioPath(path);
            Files.createDirectories(nioPath.getParent());
            switch (mode) {
                case APPEND:
                    return Files.newOutputStream(nioPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                case TRUNCATE:
                    return Files.newOutputStream(nioPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                case CREATE:
                default:
                    return Files.newOutputStream(nioPath, StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException | SecurityException e) {
            throw new FileSystemException("Unable to open for write: " + path.path(), e);
        }
    }

    @Override
    public FileNode createFile(FilePath path) throws FileSystemException {
        requireLocal(path);
        try {
            Path nioPath = toNioPath(path);
            Files.createDirectories(nioPath.getParent());
            Files.createFile(nioPath);
            return new LocalFileNode(nioPath.toFile());
        } catch (IOException | SecurityException e) {
            throw new FileSystemException("Unable to create file: " + path.path(), e);
        }
    }

    @Override
    public FileNode createDirectory(FilePath path) throws FileSystemException {
        requireLocal(path);
        try {
            Path nioPath = toNioPath(path);
            Files.createDirectories(nioPath);
            return new LocalFileNode(nioPath.toFile());
        } catch (IOException | SecurityException e) {
            throw new FileSystemException("Unable to create directory: " + path.path(), e);
        }
    }

    @Override
    public void rename(FilePath src, FilePath dst) throws FileSystemException {
        requireLocal(src);
        requireLocal(dst);
        try {
            Files.createDirectories(toNioPath(dst).getParent());
            Files.move(toNioPath(src), toNioPath(dst), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | SecurityException e) {
            throw new FileSystemException("Unable to rename " + src.path() + " to " + dst.path(), e);
        }
    }

    @Override
    public void delete(FilePath path, DeleteOptions opts) throws FileSystemException {
        requireLocal(path);
        try {
            if (opts.permanent()) {
                deleteRecursively(toNioPath(path));
            } else {
                moveToTrash(toNioPath(path));
            }
        } catch (IOException | SecurityException e) {
            throw new FileSystemException("Unable to delete: " + path.path(), e);
        }
    }

    @Override
    public boolean isSameVolume(FilePath a, FilePath b) {
        return a.isLocal() && b.isLocal() && toNioPath(a).getRoot().equals(toNioPath(b).getRoot());
    }

    @Override
    public boolean exists(FilePath path) {
        return path.isLocal() && Files.exists(toNioPath(path));
    }

    @Override
    public boolean supportsWrite() {
        return true;
    }

    @Override
    public long freeSpaceBytes(FilePath path) {
        return toNioPath(path).toFile().getFreeSpace();
    }

    @Override
    public Closeable watch(FilePath dir, WatchListener listener) {
        return null;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path) && !Files.isSymbolicLink(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    private static void moveToTrash(Path source) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        Path storageRoot = storageRootFor(source);
        String id = UUID.randomUUID().toString();
        String originalName = source.getFileName().toString();
        Path trashRoot = storageRoot.resolve(".AppTrash");
        Path trashFileDir = trashRoot.resolve("files").resolve(id);
        Path trashInfoDir = trashRoot.resolve("info");
        Files.createDirectories(trashFileDir);
        Files.createDirectories(trashInfoDir);
        Path trashPath = trashFileDir.resolve(originalName);
        try {
            Files.move(source, trashPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            Files.move(source, trashPath);
        }
        String json = "{\n"
                + "  \"id\": \"" + escapeJson(id) + "\",\n"
                + "  \"originalPath\": \"" + escapeJson(source.toString()) + "\",\n"
                + "  \"trashPath\": \"" + escapeJson(trashPath.toString()) + "\",\n"
                + "  \"displayName\": \"" + escapeJson(originalName) + "\",\n"
                + "  \"deletedAt\": " + System.currentTimeMillis() + "\n"
                + "}\n";
        Files.write(trashInfoDir.resolve(id + ".json"), json.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE_NEW);
    }

    private static Path storageRootFor(Path source) {
        Path normalized = source.toAbsolutePath().normalize();
        String path = normalized.toString().replace('\\', '/');
        if (path.startsWith("/storage/emulated/0/") || path.equals("/storage/emulated/0")) {
            return Path.of("/storage/emulated/0");
        }
        if (path.startsWith("/sdcard/") || path.equals("/sdcard")) {
            return Path.of("/sdcard");
        }
        Path root = normalized.getRoot();
        return root == null ? normalized.getParent() : root;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void requireLocal(FilePath path) throws FileSystemException {
        if (!path.isLocal()) {
            throw new FileSystemException("Local provider cannot handle: " + path);
        }
    }

    private static Path toNioPath(FilePath path) {
        String raw = path.path();
        if (System.getProperty("os.name", "").toLowerCase().contains("win")
                && raw.matches("^/[A-Za-z]:/.*")) {
            raw = raw.substring(1);
        }
        return Path.of(raw);
    }
}
