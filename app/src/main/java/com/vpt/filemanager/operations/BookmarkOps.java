package com.vpt.filemanager.operations;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import androidx.lifecycle.LiveData;

import com.vpt.filemanager.data.db.dao.BookmarkDao;
import com.vpt.filemanager.data.db.entity.BookmarkEntryEntity;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Quản lý bookmark folder/file. Pure Room — không đụng FS layer. Click bookmark từ drawer sẽ
 * resolve path → LocalSource.resolve() để dựng VirtualNode mở folder thật. Đây là lý do
 * BookmarkOps không cần biết NodeSource.
 *
 * <p>v1 scope: chỉ bookmark folder (UI sẽ disable BOOKMARK action cho file ở Phase R-8). Schema
 * vẫn nhận file để forward-compat — đổi UI rule là 1 dòng, không phải migrate schema.
 *
 * <p>Idempotent add: nếu path đã bookmarked thì no-op (không throw). Phù hợp với UX bottom sheet
 * "Add bookmark" trên cùng item nhiều lần.
 */
@Singleton
public final class BookmarkOps {
    private final BookmarkDao dao;

    @Inject
    public BookmarkOps(BookmarkDao dao) {
        this.dao = dao;
    }

    /**
     * Thêm bookmark cho node. No-op nếu path đã có bookmark (idempotent).
     */
    public void add(VirtualNode node) throws NodeException {
        if (!node.path().isLocal()) {
            throw new NodeException("Only local paths can be bookmarked");
        }
        String path = node.path().path();
        if (dao.findByPath(path) != null) {
            return;
        }
        BookmarkEntryEntity entity = new BookmarkEntryEntity();
        entity.id = UUID.randomUUID().toString();
        entity.path = path;
        entity.displayName = node.name();
        entity.addedAtMillis = System.currentTimeMillis();
        Integer maxPos = dao.maxPosition();
        entity.position = (maxPos == null ? 0 : maxPos + 1);
        dao.insert(entity);
    }

    /** Xóa bookmark theo path. No-op nếu không có (idempotent). */
    public void remove(VirtualNode node) {
        dao.deleteByPath(node.path().path());
    }

    /**
     * Xóa bookmark theo local path string trực tiếp. Dùng khi caller chỉ có {@link FilePath}
     * mà target file có thể đã bị xóa khỏi disk — tránh phải resolve VirtualNode (sẽ fail) chỉ
     * để remove row. No-op nếu path không có bookmark.
     */
    public void removeByPath(FilePath path) {
        if (!path.isLocal()) {
            return;
        }
        dao.deleteByPath(path.path());
    }

    public boolean isBookmarked(VirtualNode node) {
        return dao.findByPath(node.path().path()) != null;
    }

    public LiveData<List<BookmarkEntryEntity>> observeAll() {
        return dao.observeAll();
    }

    public List<BookmarkEntryEntity> all() {
        return dao.all();
    }
}
