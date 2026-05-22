package com.vpt.filemanager.operations.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.operations.trash.TrashStore;

/**
 * Facade cho file CRUD trên VirtualNode tree. Inspect node, dispatch xuống
 * {@link com.vpt.filemanager.node.source.NodeSource} write API.
 *
 * <p>Phase R-4 ship 4 op cơ bản: createFile / createFolder / rename / delete.
 *
 * <p>Phase C-1a ship copy/move với 2-axis design:
 * <ul>
 *   <li><b>Same-source local</b>: ưu tiên nio {@code Files.move} với ATOMIC fallback → O(1) rename
 *       cùng filesystem, copy+delete khi qua filesystem khác (vd SD card).</li>
 *   <li><b>Cross-source / non-local</b>: generic stream copy qua {@link VirtualNode#openRead} →
 *       {@link VirtualNode#openWrite}. Folder = đệ quy children + createFolder per level.</li>
 *   <li><b>Conflict</b>: NodeFileBackend không tự resolve — caller (Worker / VM) check tồn tại trước và
 *       chọn policy. {@link CancellationToken} cho phép Worker abort mid-stream.</li>
 * </ul>
 *
 * <p>Trash soft-delete KHÔNG đi qua NodeFileBackend — dùng {@link TrashStore#moveToTrash(VirtualNode)}.
 * NodeFileBackend.delete() là PERMANENT.
 *
 * <p>Stateless singleton — không hold reference VM/Fragment. Caller (PaneViewModel / Worker) wrap
 * call trong {@code executors.io().submit(...)} + catch NodeException ở boundary để format
 * Toast/Dialog.
 */
@Singleton
public final class NodeFileBackend {
    private static final int COPY_BUFFER_BYTES = 64 * 1024;

    @Inject
    public NodeFileBackend() {
    }

    /**
     * Token huỷ tác vụ stream copy. Worker set {@code cancelled} từ outside; NodeFileBackend check sau mỗi
     * buffer write. Tách khỏi {@code Thread.interrupt()} để Worker có thể quản lý semantic cancel
     * riêng (vd "Skip this item" ≠ "Cancel all").
     */
    public static final class CancellationToken {
        private final AtomicBoolean flag = new AtomicBoolean(false);

        public void cancel() {
            flag.set(true);
        }

        public boolean isCancelled() {
            return flag.get();
        }

        public static CancellationToken neverCancelled() {
            return new CancellationToken();
        }
    }

    public VirtualNode createFile(VirtualNode parentFolder, String name) throws NodeException {
        validateWritableFolder(parentFolder);
        NodePath childPath = parentFolder.path().child(name);
        return parentFolder.source().createFile(childPath);
    }

    public VirtualNode createFolder(VirtualNode parentFolder, String name) throws NodeException {
        validateWritableFolder(parentFolder);
        NodePath childPath = parentFolder.path().child(name);
        return parentFolder.source().createFolder(childPath);
    }

    /**
     * Đổi tên trong cùng folder. Tên mới validate cơ bản (không rỗng, không chứa '/').
     * NodePath.child() đã throw nếu name chứa separator.
     */
    public VirtualNode rename(VirtualNode node, String newName) throws NodeException {
        if (newName == null || newName.isBlank()) {
            throw new NodeException("New name is empty");
        }
        validateWritable(node);
        return node.source().rename(node, newName.trim());
    }

    /**
     * <b>Permanent delete</b>. Folder = xóa đệ quy. Gọi {@link TrashStore#moveToTrash} cho soft delete.
     */
    public void delete(VirtualNode node) throws NodeException {
        validateWritable(node);
        node.source().delete(node);
    }

    // ─────────────────────────── Copy / Move (Phase C-1a) ───────────────────────────

    /**
     * Copy node (file hoặc folder) vào {@code targetParent} với tên {@code newName}. Conflict
     * resolution là việc của caller — NodeFileBackend throw nếu {@code newName} đã có trong targetParent.
     *
     * <p>Folder copy đệ quy — file children copy qua stream, sub-folder gọi đệ quy. Partial copy
     * khi fail giữa chừng (vd disk full): các entry đã copy sẽ giữ nguyên — caller chịu cleanup
     * (defer cancel/rollback semantics cho Phase C-1c WorkManager).
     */
    public VirtualNode copy(VirtualNode src, VirtualNode targetParent, String newName,
                            CancellationToken token) throws NodeException {
        if (src == null) {
            throw new NodeException("Source is null");
        }
        validateNewName(newName);
        validateWritableFolder(targetParent);
        return src.isFolder()
                ? copyFolderInto(src, targetParent, newName, token)
                : copyFileInto(src, targetParent, newName, token);
    }

    /**
     * Move node vào {@code targetParent}. Strategy 2 tầng:
     * <ol>
     *   <li>Same-scheme local: thử {@code Files.move(ATOMIC_MOVE)} — O(1) khi cùng filesystem.
     *       Fallback {@code Files.move()} non-atomic — nio tự copy+delete khi qua filesystem khác
     *       (vd SD card / OTG).</li>
     *   <li>Cross-source (vd archive entry → local): {@link #copy} + {@code source.delete(src)}.
     *       Yêu cầu {@code src.source().supportsWrite()} = true → archive → throw fail-fast.</li>
     * </ol>
     */
    public VirtualNode move(VirtualNode src, VirtualNode targetParent, String newName,
                            CancellationToken token) throws NodeException {
        if (src == null) {
            throw new NodeException("Source is null");
        }
        validateNewName(newName);
        validateWritable(src);
        validateWritableFolder(targetParent);
        // Same-source local fast path: nio Files.move với atomic-then-fallback semantics.
        if (src.path().isLocal() && targetParent.path().isLocal()
                && src.source() == targetParent.source()) {
            return moveLocalNio(src, targetParent, newName);
        }
        VirtualNode copied = copy(src, targetParent, newName, token);
        if (token.isCancelled()) {
            // copy() đã tự cleanup partial dst khi cancel — leave src untouched (move = no-op visible)
            return copied;
        }
        try {
            src.source().delete(src);
        } catch (NodeException deleteFail) {
            // Compensation: copy thành công nhưng delete src fail → rollback dst để giữ atomic
            // semantic "all-or-nothing visible". Nếu rollback FAIL → torn state (cả src + dst
            // tồn tại), throw với message rõ để user thấy + manual cleanup.
            try {
                copied.source().delete(copied);
            } catch (NodeException compensationFail) {
                throw new NodeException("Move failed mid-step — both src and dst exist (manual"
                        + " cleanup needed): " + src.name(), deleteFail);
            }
            throw new NodeException("Move failed (rolled back): " + src.name(), deleteFail);
        }
        return copied;
    }

    private VirtualNode moveLocalNio(VirtualNode src, VirtualNode targetParent, String newName)
            throws NodeException {
        NodePath newPath = targetParent.path().child(newName);
        Path srcNio = toNioLocal(src.path());
        Path dstNio = toNioLocal(newPath);
        try {
            Path parent = dstNio.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try {
                Files.move(srcNio, dstNio, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException atomicNotSupported) {
                // Cross-filesystem (vd internal → SD card) hoặc Android FUSE — nio sẽ tự copy+delete.
                Files.move(srcNio, dstNio);
            }
            return targetParent.source().resolve(newPath);
        } catch (IOException | SecurityException e) {
            throw new NodeException("Move failed: " + src.name() + " → " + newName, e);
        }
    }

    private VirtualNode copyFileInto(VirtualNode src, VirtualNode parent, String newName,
                                     CancellationToken token) throws NodeException {
        NodePath dstPath = parent.path().child(newName);
        VirtualNode dst = parent.source().createFile(dstPath);
        try (InputStream in = src.openRead(); OutputStream out = dst.openWrite()) {
            byte[] buf = new byte[COPY_BUFFER_BYTES];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (token.isCancelled()) {
                    throw new IOException("Copy cancelled");
                }
                out.write(buf, 0, n);
            }
        } catch (IOException | SecurityException e) {
            // Partial dst → cleanup best-effort (nếu source hỗ trợ delete).
            try {
                parent.source().delete(dst);
            } catch (NodeException ignored) {
                // Cleanup fail không che lỗi gốc — chỉ swallow.
            }
            throw new NodeException("Copy failed: " + src.name() + " → " + newName, e);
        }
        return parent.source().resolve(dstPath);
    }

    private VirtualNode copyFolderInto(VirtualNode src, VirtualNode parent, String newName,
                                       CancellationToken token) throws NodeException {
        NodePath dstPath = parent.path().child(newName);
        VirtualNode newFolder = parent.source().createFolder(dstPath);
        List<VirtualNode> children = src.children();
        try {
            for (VirtualNode child : children) {
                if (token.isCancelled()) {
                    throw new NodeException("Copy cancelled: " + src.name());
                }
                if (child.isFolder()) {
                    copyFolderInto(child, newFolder, child.name(), token);
                } else {
                    copyFileInto(child, newFolder, child.name(), token);
                }
            }
        } catch (NodeException e) {
            // Partial folder + đã copy được vài child → cleanup recursive để cancel/fail là
            // observable "no-op" cho user. Cleanup fail nuốt silently để không che lỗi gốc.
            try {
                parent.source().delete(newFolder);
            } catch (NodeException ignored) {
            }
            throw e;
        }
        return newFolder;
    }

    private static Path toNioLocal(NodePath path) {
        String raw = path.path();
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                && raw.matches("^/[A-Za-z]:/.*")) {
            raw = raw.substring(1);
        }
        return Paths.get(raw);
    }

    private static void validateNewName(String newName) throws NodeException {
        if (newName == null || newName.isBlank()) {
            throw new NodeException("New name is empty");
        }
        if (newName.contains("/") || newName.contains("\\")) {
            throw new NodeException("Name must not contain path separators: " + newName);
        }
    }

    private static void validateWritable(VirtualNode node) throws NodeException {
        if (!node.source().supportsWrite()) {
            throw new NodeException("Source is read-only: " + node.path().scheme());
        }
    }

    private static void validateWritableFolder(VirtualNode parent) throws NodeException {
        if (!parent.isFolder()) {
            throw new NodeException("Parent must be a folder: " + parent.path());
        }
        validateWritable(parent);
    }
}
