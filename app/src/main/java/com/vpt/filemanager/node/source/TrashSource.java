package com.vpt.filemanager.node.source;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import timber.log.Timber;

import com.vpt.filemanager.data.db.dao.TrashDao;
import com.vpt.filemanager.data.db.entity.TrashEntryEntity;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;

/**
 * {@link NodeSource} impl cho Trash — opaque virtual source backed by {@link TrashDao}. Mỗi
 * trash entry được render thành 1 {@link VirtualNode} với scheme {@code trash}, authority = entry
 * UUID (đảm bảo unique kể cả khi 2 entry trùng display name), path = {@code /displayName}.
 *
 * <p><b>Click semantic</b>: trashed node KHÔNG mở được — UI tap-as-select gate ở
 * {@code PaneFragment} (xem R-7b). {@link #read} throw để defense-in-depth nếu opener nhỡ gọi tới.
 *
 * <p><b>Write API</b>: read-only — {@code TrashOps} sở hữu flow restore/empty riêng (xem
 * {@link com.vpt.filemanager.operations.TrashOps}). v1 không cho rename/createFile trong Trash.
 *
 * <p><b>Folder browsing</b>: v1 chỉ list root. Trashed folder không expandable (entry là 1 blob)
 * — nếu cần inspect bên trong, user phải restore trước. Phase 2D có thể cân nhắc browse-inside.
 */
@Singleton
public final class TrashSource implements NodeSource {
    private final TrashDao dao;

    @Inject
    public TrashSource(TrashDao dao) {
        this.dao = dao;
    }

    @Override
    public VirtualNode resolve(FilePath path) throws NodeException {
        if (!path.isTrash()) {
            throw new NodeException("TrashSource cannot resolve scheme: " + path.scheme());
        }
        // trash:/// (root) → virtual folder, no FS backing
        if (path.authority().isEmpty() && "/".equals(path.path())) {
            return new VirtualNode(path, true, -1L, -1L, this);
        }
        // trash://{id}/{name} → single entry lookup
        TrashEntryEntity entity = dao.findById(path.authority());
        if (entity == null) {
            throw new NodeException("Trash entry not found: " + path.authority());
        }
        // Defensive (Codex review): nếu Room row tồn tại mà FS blob đã biến mất (partial
        // restore/delete-forever fail), log để dev phát hiện. Vẫn return node — UI sẽ thấy entry
        // và user có thể chọn restore → flow đó sẽ throw clearer "File no longer exists".
        if (!Files.exists(Path.of(entity.trashPath))) {
            Timber.w("Stale trash row (blob missing): id=%s path=%s", entity.id, entity.trashPath);
        }
        return buildNode(entity);
    }

    @Override
    public List<VirtualNode> list(VirtualNode folder) throws NodeException {
        FilePath dirPath = folder.path();
        if (!dirPath.isTrash()) {
            throw new NodeException("TrashSource cannot list scheme: " + dirPath.scheme());
        }
        // v1: chỉ root listable. Trashed folder không browse-inside.
        if (!dirPath.authority().isEmpty()) {
            throw new NodeException("Trashed folders cannot be browsed — restore first");
        }
        List<TrashEntryEntity> entries = dao.all();
        List<VirtualNode> nodes = new ArrayList<>(entries.size());
        for (TrashEntryEntity entity : entries) {
            nodes.add(buildNode(entity));
        }
        return nodes;
    }

    @Override
    public InputStream read(VirtualNode file) throws NodeException {
        throw new NodeException("Cannot open trashed entry — restore first");
    }

    // ─────────────── Write API: Trash read-only (TrashOps owns mutation) ───────────────

    @Override
    public boolean supportsWrite() {
        return false;
    }

    @Override
    public VirtualNode createFile(FilePath path) throws NodeException {
        throw new NodeException("Cannot create inside Trash");
    }

    @Override
    public VirtualNode createFolder(FilePath path) throws NodeException {
        throw new NodeException("Cannot create inside Trash");
    }

    @Override
    public VirtualNode rename(VirtualNode node, String newName) throws NodeException {
        throw new NodeException("Cannot rename trashed entry — restore first");
    }

    @Override
    public void delete(VirtualNode node) throws NodeException {
        // TrashOps.emptyAll / deleteForever owns hard-delete flow. NodeSource.delete() được
        // FileOps.delete gate qua supportsWrite=false trước khi gọi → never reach đây từ FileOps.
        throw new NodeException("Use TrashOps for trash mutations");
    }

    /**
     * Map {@link TrashEntryEntity} sang {@link VirtualNode}. UUID làm authority để 2 file cùng
     * tên (vd 2 file "notes.txt" xóa từ 2 folder) hiển thị riêng biệt với key unique.
     */
    private VirtualNode buildNode(TrashEntryEntity entity) {
        FilePath path = new FilePath(FilePath.SCHEME_TRASH, entity.id, "/" + entity.displayName);
        return new VirtualNode(path, entity.directory,
                entity.directory ? -1L : entity.sizeBytes,
                entity.deletedAtMillis, this);
    }
}
