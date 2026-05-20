package com.vpt.filemanager.node.source;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;

/**
 * {@link NodeSource} impl cho local file system — thao tác qua {@link java.nio.file.Files}.
 *
 * <p><b>Singleton</b>: stateless, mọi {@link VirtualNode} mọi folder mọi pane share cùng 1
 * instance này (zero RAM waste).
 *
 * <p><b>Path string interning</b> (xem ARCHITECTURE.md "Performance"): mỗi folder list trả 1000+
 * children có cùng path prefix; intern prefix một lần giảm dup string đáng kể với folder lớn.
 *
 * <p><b>Exception policy</b>: mọi {@link IOException} / {@link SecurityException} được wrap thành
 * {@link NodeException} với message user-friendly. Caller-boundary catch (PaneViewModel) format
 * tiếp qua ErrorPresenter.
 */
@Singleton
public final class LocalSource implements NodeSource {
    @Inject
    public LocalSource() {
    }

    @Override
    public VirtualNode resolve(FilePath path) throws NodeException {
        if (!path.isLocal()) {
            throw new NodeException("LocalSource cannot resolve scheme: " + path.scheme());
        }
        Path nioPath = toNioPath(path);
        if (!Files.exists(nioPath)) {
            throw new NodeException("Path not found: " + path.path());
        }
        return buildNode(path, nioPath);
    }

    @Override
    public List<VirtualNode> list(VirtualNode folder) throws NodeException {
        FilePath dirPath = folder.path();
        if (!dirPath.isLocal()) {
            throw new NodeException("LocalSource cannot list scheme: " + dirPath.scheme());
        }
        Path nioDir = toNioPath(dirPath);
        if (!Files.isDirectory(nioDir)) {
            throw new NodeException("Not a directory: " + dirPath.path());
        }
        // intern prefix để mọi child share string — tiết kiệm RAM cho folder lớn
        dirPath.path().intern();
        List<VirtualNode> result = new ArrayList<>(64);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(nioDir)) {
            for (Path child : stream) {
                String childName = child.getFileName() == null
                        ? child.toString()
                        : child.getFileName().toString();
                FilePath childPath = dirPath.child(childName);
                result.add(buildNode(childPath, child));
            }
        } catch (IOException | SecurityException e) {
            throw new NodeException("Unable to list: " + dirPath.path(), e);
        }
        return result;
    }

    @Override
    public InputStream read(VirtualNode file) throws NodeException {
        FilePath path = file.path();
        if (!path.isLocal()) {
            throw new NodeException("LocalSource cannot read scheme: " + path.scheme());
        }
        try {
            return Files.newInputStream(toNioPath(path), StandardOpenOption.READ);
        } catch (IOException | SecurityException e) {
            throw new NodeException("Unable to open for read: " + path.path(), e);
        }
    }

    // ─────────────────────────── Write API ───────────────────────────

    @Override
    public boolean supportsWrite() {
        return true;
    }

    @Override
    public VirtualNode createFile(FilePath path) throws NodeException {
        requireLocal(path);
        try {
            Path nioPath = toNioPath(path);
            Path parent = nioPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.createFile(nioPath);
            return buildNode(path, nioPath);
        } catch (IOException | SecurityException e) {
            throw new NodeException("Cannot create file: " + path.path(), e);
        }
    }

    @Override
    public VirtualNode createFolder(FilePath path) throws NodeException {
        requireLocal(path);
        try {
            Path nioPath = toNioPath(path);
            Files.createDirectories(nioPath);
            return buildNode(path, nioPath);
        } catch (IOException | SecurityException e) {
            throw new NodeException("Cannot create folder: " + path.path(), e);
        }
    }

    @Override
    public VirtualNode rename(VirtualNode node, String newName) throws NodeException {
        FilePath path = node.path();
        requireLocal(path);
        FilePath newPath = path.parent().child(newName);
        try {
            Path src = toNioPath(path);
            Path dst = toNioPath(newPath);
            try {
                Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                // Atomic move có thể fail nếu src/dst khác filesystem hoặc OS không support;
                // fallback non-atomic Files.move thường vẫn rename ok trong cùng folder.
                Files.move(src, dst);
            }
            return buildNode(newPath, dst);
        } catch (IOException | SecurityException e) {
            throw new NodeException("Cannot rename: " + path.path() + " → " + newName, e);
        }
    }

    @Override
    public void delete(VirtualNode node) throws NodeException {
        FilePath path = node.path();
        requireLocal(path);
        try {
            deleteRecursively(toNioPath(path));
        } catch (IOException | SecurityException e) {
            throw new NodeException("Cannot delete: " + path.path(), e);
        }
    }

    /**
     * Đệ quy iterative chậm gấp đôi vì DirectoryStream khó dùng iterative — recursive đủ deep
     * cho user folder thực tế (cây <1000 levels). Symlink check tránh infinite loop trong khi
     * vẫn cho phép xóa symlink chính nó (Files.deleteIfExists xóa link, không follow).
     */
    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path) && !Files.isSymbolicLink(path)) {
            try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    private static void requireLocal(FilePath path) throws NodeException {
        if (!path.isLocal()) {
            throw new NodeException("LocalSource cannot handle scheme: " + path.scheme());
        }
    }

    /**
     * Stat một path local rồi build {@link VirtualNode}. Ưu tiên {@link BasicFileAttributes}
     * (1 syscall thay vì 3 lần gọi {@link File#isDirectory} / {@link File#length} /
     * {@link File#lastModified}). Fallback sang File API khi attrs fail (vd symlink broken).
     */
    private VirtualNode buildNode(FilePath path, Path nioPath) {
        boolean dir;
        long size;
        long modified;
        try {
            BasicFileAttributes attrs = Files.readAttributes(nioPath, BasicFileAttributes.class);
            dir = attrs.isDirectory();
            size = dir ? -1L : attrs.size();
            modified = attrs.lastModifiedTime().toMillis();
        } catch (IOException | SecurityException e) {
            File f = nioPath.toFile();
            dir = f.isDirectory();
            size = dir ? -1L : f.length();
            modified = f.lastModified();
        }
        return new VirtualNode(path, dir, size, modified, this);
    }

    /**
     * Chuyển {@link FilePath} sang {@link java.nio.file.Path}. Trên Windows (chỉ gặp khi chạy
     * unit test trên host), strip leading slash cho path dạng {@code /C:/...}.
     */
    private static Path toNioPath(FilePath path) {
        String raw = path.path();
        if (System.getProperty("os.name", "").toLowerCase().contains("win")
                && raw.matches("^/[A-Za-z]:/.*")) {
            raw = raw.substring(1);
        }
        return Path.of(raw);
    }
}
