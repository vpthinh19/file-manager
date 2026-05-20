package com.vpt.filemanager.opener;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Strategy về HÀNH VI khi user click một {@link VirtualNode} file. Trục thứ 2 trong concept
 * DOM ảo (orthogonal với {@link com.vpt.filemanager.node.source.NodeSource} là trục NGUỒN).
 *
 * <p>Mỗi loại file đứng ngang hàng: TextOpener / ArchiveOpener / ImageOpener / VideoOpener /
 * AudioOpener / ExternalOpener. Thêm loại mới (vd PdfOpener Phase 3) = thêm 1 file impl, KHÔNG
 * sửa node/, KHÔNG sửa browser/.
 *
 * <p>Folder click KHÔNG đi qua FileOpener — caller (PaneViewModel) tự navigate vào folder.
 *
 * <p>Registry chọn opener theo priority list (xem {@link OpenerRegistry}). Opener đầu tiên có
 * {@link #canOpen(VirtualNode)} trả {@code true} sẽ được dùng — vì vậy specific opener
 * (TextOpener) phải đứng TRƯỚC fallback (ExternalOpener) trong list.
 */
public interface FileOpener {
    /**
     * Opener này có handle được {@code node} không. KHÔNG side-effect; chỉ inspect metadata.
     *
     * <p>Quy ước: trả {@code false} nếu node là folder hoặc không match loại file của opener.
     */
    boolean canOpen(VirtualNode node);

    /**
     * Thực thi action mở file. Có thể launch Activity, push fragment, navigate pane,...
     *
     * @throws NodeException khi mở fail (vd archive lỗi, không có app handle ACTION_VIEW)
     */
    void onOpen(VirtualNode node, OpenContext ctx) throws NodeException;
}
