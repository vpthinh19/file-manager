package com.vpt.filemanager.node.source;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import timber.log.Timber;

import com.vpt.filemanager.data.db.dao.BookmarkDao;
import com.vpt.filemanager.data.db.entity.BookmarkEntryEntity;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;

/**
 * {@link NodeSource} impl cho Bookmark — transparent passthrough vào {@link LocalSource}. Bookmark
 * root được render qua scheme {@code bookmark}, nhưng children trả về scheme {@code file} (đã
 * resolve qua {@link LocalSource}). Hệ quả:
 *
 * <ul>
 *   <li>Click 1 bookmark folder trong pane → navigate vào folder thật (scheme=file) — opener flow
 *       chuẩn, không có code path đặc biệt.</li>
 *   <li>Long-press bookmark → selection mode với scheme=file path → {@link com.vpt.filemanager.operations.BookmarkOps#remove}
 *       xoá theo path lookup. UI layer detect "pane đang ở bookmark root" qua active VM
 *       currentPath().scheme(), không qua path của children.</li>
 * </ul>
 *
 * <p><b>Missing target</b>: nếu bookmark trỏ tới folder đã bị xóa, {@link LocalSource#resolve}
 * throw {@link NodeException} — list() skip silently + Timber log. Drop-bookmark-on-broken là việc
 * của user (qua remove action) hoặc Phase 2D cleanup job.
 *
 * <p><b>Write API</b>: read-only — BookmarkOps sở hữu add/remove. Bookmark root không support
 * createFile/createFolder/rename (semantic: bookmark = shortcut, không phải folder thật).
 */
@Singleton
public final class BookmarkSource implements NodeSource {
    private final BookmarkDao dao;
    private final LocalSource localSource;

    @Inject
    public BookmarkSource(BookmarkDao dao, LocalSource localSource) {
        this.dao = dao;
        this.localSource = localSource;
    }

    @Override
    public VirtualNode resolve(NodePath path) throws NodeException {
        if (!path.isBookmark()) {
            throw new NodeException("BookmarkSource cannot resolve scheme: " + path.scheme());
        }
        // bookmark:/// (root) là entry point duy nhất. Children luôn scheme=file → resolve qua
        // LocalSource → bookmark scheme không tồn tại ngoài root.
        if (path.authority().isEmpty() && "/".equals(path.path())) {
            return new VirtualNode(path, true, -1L, -1L, this);
        }
        throw new NodeException("Bookmark scheme only resolves at root — children pass through to file");
    }

    @Override
    public List<VirtualNode> list(VirtualNode folder) throws NodeException {
        NodePath dirPath = folder.path();
        if (!dirPath.isBookmark()) {
            throw new NodeException("BookmarkSource cannot list scheme: " + dirPath.scheme());
        }
        List<BookmarkEntryEntity> entries = dao.all();
        List<VirtualNode> nodes = new ArrayList<>(entries.size());
        for (BookmarkEntryEntity entity : entries) {
            try {
                nodes.add(localSource.resolve(NodePath.local(entity.path)));
            } catch (NodeException broken) {
                // Bookmark trỏ tới folder/file đã bị xoá → skip listing, không fail toàn bộ.
                // User có thể remove bookmark thủ công sau. Phase 2D cân nhắc auto-prune.
                Timber.w(broken, "Skipping broken bookmark: %s", entity.path);
            }
        }
        return nodes;
    }

    @Override
    public InputStream read(VirtualNode file) throws NodeException {
        throw new NodeException("Bookmark is a shortcut layer — open via the underlying file path");
    }

    @Override
    public OutputStream openWrite(VirtualNode file) throws NodeException {
        throw new NodeException("Bookmark is a shortcut layer — write via the underlying file path");
    }

    // ─────────────── Write API: Bookmark read-only (BookmarkOps owns mutation) ───────────────

    @Override
    public boolean supportsWrite() {
        return false;
    }

    @Override
    public VirtualNode createFile(NodePath path) throws NodeException {
        throw new NodeException("Cannot create inside Bookmark");
    }

    @Override
    public VirtualNode createFolder(NodePath path) throws NodeException {
        throw new NodeException("Cannot create inside Bookmark");
    }

    @Override
    public VirtualNode rename(VirtualNode node, String newName) throws NodeException {
        throw new NodeException("Cannot rename bookmark — remove and re-add with new name");
    }

    @Override
    public void delete(VirtualNode node) throws NodeException {
        throw new NodeException("Use BookmarkOps for bookmark mutations");
    }
}
