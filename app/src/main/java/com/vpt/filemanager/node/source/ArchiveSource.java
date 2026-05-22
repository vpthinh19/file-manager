package com.vpt.filemanager.node.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;

/**
 * {@link NodeSource} impl cho archive (zip) — read-only browsing.
 *
 * <p><b>Session cache</b>: mở {@link ZipFile} là syscall + parse central directory (~ms-100ms cho
 * zip lớn). Mỗi archive file mở 1 lần, cache trong {@code sessions} map, sống đến hết app
 * lifetime. Slight memory cost (~KB per cached zip), bù lại không re-open mỗi lần navigate inner
 * folder.
 *
 * <p><b>NodePath shape</b>: archive entry có scheme = {@code archive}, authority = path tới
 * archive file (đã URL-encoded), path = inner path bên trong zip ({@code /} = archive root).
 * Ví dụ: {@code archive://file%3A%2F%2F%2Fstorage%2Fphotos.zip/photos/cat.jpg}.
 *
 * <p><b>Capability v1</b>: chỉ read. Phase 2C-6 thêm extract qua {@code FileOps.copy}, phase
 * 3 thêm libarchive native cho 7z/RAR/TAR.
 *
 * <p><b>Thread-safety</b>: {@link ZipFile} thread-safe cho concurrent reads (Javadoc). Cache
 * map là {@link ConcurrentHashMap}.
 */
@Singleton
public final class ArchiveSource implements NodeSource {
    private final Map<NodePath, ZipFile> sessions = new ConcurrentHashMap<>();

    @Inject
    public ArchiveSource() {
    }

    @Override
    public VirtualNode resolve(NodePath path) throws NodeException {
        if (!path.isArchive()) {
            throw new NodeException("ArchiveSource cannot resolve scheme: " + path.scheme());
        }
        // archive://...!/ → root folder của archive
        if ("/".equals(path.path())) {
            return new VirtualNode(path, true, -1L, -1L, this);
        }
        NodePath archiveFilePath = NodePath.parse(path.authority());
        ZipFile zip = openOrCache(archiveFilePath);
        String inner = stripLeadingSlash(path.path());
        ZipEntry entry = zip.getEntry(inner);
        if (entry == null) {
            // Thử tìm dưới dạng folder (zip lưu folder với suffix '/')
            entry = zip.getEntry(inner + "/");
            if (entry == null) {
                throw new NodeException("Archive entry not found: " + inner);
            }
        }
        boolean dir = entry.isDirectory();
        return new VirtualNode(path, dir, dir ? -1L : entry.getSize(), entry.getTime(), this);
    }

    @Override
    public List<VirtualNode> list(VirtualNode folder) throws NodeException {
        NodePath dirPath = folder.path();
        if (!dirPath.isArchive()) {
            throw new NodeException("ArchiveSource cannot list scheme: " + dirPath.scheme());
        }
        NodePath archiveFilePath = NodePath.parse(dirPath.authority());
        ZipFile zip = openOrCache(archiveFilePath);
        return listImmediateChildren(zip, archiveFilePath, dirPath.path());
    }

    @Override
    public InputStream read(VirtualNode file) throws NodeException {
        NodePath filePath = file.path();
        if (!filePath.isArchive()) {
            throw new NodeException("ArchiveSource cannot read scheme: " + filePath.scheme());
        }
        NodePath archiveFilePath = NodePath.parse(filePath.authority());
        ZipFile zip = openOrCache(archiveFilePath);
        String inner = stripLeadingSlash(filePath.path());
        ZipEntry entry = zip.getEntry(inner);
        if (entry == null || entry.isDirectory()) {
            throw new NodeException("Archive entry not found: " + inner);
        }
        try {
            return zip.getInputStream(entry);
        } catch (IOException e) {
            throw new NodeException("Unable to open archive entry: " + inner, e);
        }
    }

    // ─────────────── Write API: archive v1 read-only ───────────────
    // Phase 3 sẽ wire libarchive native cho write support nếu khả thi. ZipFile java.util.zip
    // không support modify in-place — phải write toàn bộ ra file mới. v1 không investment.

    @Override
    public OutputStream openWrite(VirtualNode file) throws NodeException {
        throw new NodeException("Archive write is not supported in v1");
    }

    @Override
    public boolean supportsWrite() {
        return false;
    }

    @Override
    public VirtualNode createFile(NodePath path) throws NodeException {
        throw new NodeException("Archive write is not supported in v1");
    }

    @Override
    public VirtualNode createFolder(NodePath path) throws NodeException {
        throw new NodeException("Archive write is not supported in v1");
    }

    @Override
    public VirtualNode rename(VirtualNode node, String newName) throws NodeException {
        throw new NodeException("Archive write is not supported in v1");
    }

    @Override
    public void delete(VirtualNode node) throws NodeException {
        throw new NodeException("Archive write is not supported in v1");
    }

    /**
     * Lấy {@link ZipFile} từ cache, hoặc mở mới và cache lại. Mở zip có thể tốn (parse central
     * directory) — cache phẳng tránh re-open mỗi navigate.
     */
    private ZipFile openOrCache(NodePath archiveFilePath) throws NodeException {
        ZipFile existing = sessions.get(archiveFilePath);
        if (existing != null) {
            return existing;
        }
        try {
            ZipFile fresh = new ZipFile(archiveFilePath.path());
            ZipFile prev = sessions.putIfAbsent(archiveFilePath, fresh);
            if (prev != null) {
                // Race condition: another thread cached first → đóng instance vừa mở
                try {
                    fresh.close();
                } catch (IOException ignored) {
                }
                return prev;
            }
            return fresh;
        } catch (IOException e) {
            throw new NodeException("Unable to open archive: " + archiveFilePath.path(), e);
        }
    }

    /**
     * Liệt kê immediate children dưới {@code innerDir} trong zip. {@link ZipFile} lưu entries
     * dạng flat (vd "a/b/c.txt") — phải parse prefix + tách "immediate" theo dấu '/' đầu tiên
     * sau prefix. {@link Set} dedup tránh trùng tên (zip lưu cả entry folder "a/" và entry file
     * "a/b" → "a" xuất hiện 2 lần).
     */
    private List<VirtualNode> listImmediateChildren(ZipFile zip, NodePath archiveFilePath,
                                                     String innerDir) {
        String prefix = normalizeEntryPrefix(innerDir);
        Set<String> directNames = new HashSet<>();
        List<VirtualNode> nodes = new ArrayList<>(32);
        zip.stream().forEach(entry -> {
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
            boolean dir = slash >= 0 || entry.isDirectory();
            NodePath childPath = NodePath.inArchive(archiveFilePath, joinInner(innerDir, direct));
            nodes.add(new VirtualNode(childPath, dir,
                    dir ? -1L : entry.getSize(), entry.getTime(), this));
        });
        return nodes;
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

    private static String joinInner(String parent, String child) {
        if ("/".equals(parent)) {
            return "/" + child;
        }
        return parent + "/" + child;
    }
}
