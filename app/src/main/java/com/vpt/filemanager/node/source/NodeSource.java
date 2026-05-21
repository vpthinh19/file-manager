package com.vpt.filemanager.node.source;

import java.io.InputStream;
import java.util.List;

import com.vpt.filemanager.node.FilePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Strategy abstraction cho NGUỒN dữ liệu của một {@link VirtualNode}. Mỗi nguồn (local disk, zip
 * archive, Room-backed trash, Room-backed bookmark, future cloud) implement interface này.
 *
 * <p>Khái niệm cốt lõi của DOM ảo: VirtualNode chỉ là identity (path/name/size/...), còn cách đọc
 * children và bytes ẩn hoàn toàn trong NodeSource. UI/browser/opener không biết source là gì —
 * chỉ gọi {@link VirtualNode#children()} hoặc {@link VirtualNode#openRead()} là xong.
 *
 * <p>Mọi impl PHẢI thread-safe: nhiều pane có thể đồng thời gọi {@code list()} qua cùng instance.
 * Stateless impl (LocalSource, TrashSource, BookmarkSource) là singleton Hilt; stateful impl
 * (ArchiveSource) tự quản handle cache nội bộ.
 */
public interface NodeSource {
    /**
     * Khởi tạo một {@link VirtualNode} từ {@link FilePath} — stat metadata (isFolder/size/
     * modifiedAt) tại đây. Dùng khi navigation bắt đầu từ một path chứ không phải từ parent
     * listing (ví dụ: restore từ SavedStateHandle, pop back stack, drawer click vào virtual root).
     *
     * @throws NodeException khi path không tồn tại hoặc không thuộc scheme mà source này handle
     */
    VirtualNode resolve(FilePath path) throws NodeException;

    /**
     * Liệt kê children của một folder. Caller phải đảm bảo {@code folder.isFolder() == true}.
     *
     * @throws NodeException khi folder không tồn tại, không có quyền đọc, hoặc backend lỗi
     */
    List<VirtualNode> list(VirtualNode folder) throws NodeException;

    /**
     * Mở stream đọc nội dung của một file. Caller phải đảm bảo {@code file.isFolder() == false}.
     *
     * @throws NodeException khi file không tồn tại, không có quyền đọc, hoặc backend lỗi
     */
    InputStream read(VirtualNode file) throws NodeException;

    // ─────────────────────────── Write API (Phase R-4) ───────────────────────────
    // Source nào không support write (vd ArchiveSource v1) phải override các method này throw
    // NodeException với message rõ. FileOps facade gate qua supportsWrite() trước khi gọi.

    /**
     * {@code true} nếu source này hỗ trợ create/rename/delete. ArchiveSource v1 = false.
     * Caller (FileOps) check trước khi gọi write methods để fail-fast với message rõ thay vì
     * bắt NodeException ở layer thấp.
     */
    boolean supportsWrite();

    /**
     * Tạo file mới rỗng tại {@code path}. Parent directories phải tự được tạo nếu thiếu.
     *
     * @return VirtualNode đại diện file vừa tạo (với metadata fresh)
     * @throws NodeException khi parent không tồn tại, path đã có entry, hoặc source read-only
     */
    VirtualNode createFile(FilePath path) throws NodeException;

    /**
     * Tạo folder mới (recursive — tự tạo parent nếu thiếu).
     *
     * @return VirtualNode đại diện folder vừa tạo
     * @throws NodeException khi source read-only hoặc IO lỗi
     */
    VirtualNode createFolder(FilePath path) throws NodeException;

    /**
     * Đổi tên node trong cùng parent. Atomic nếu có thể (qua {@link java.nio.file.StandardCopyOption#ATOMIC_MOVE}).
     *
     * @return VirtualNode mới với path đã rename + metadata refresh
     * @throws NodeException khi tên mới đụng entry tồn tại, source read-only, hoặc IO lỗi
     */
    VirtualNode rename(VirtualNode node, String newName) throws NodeException;

    /**
     * Xóa permanent (không qua Trash). Folder = xóa đệ quy.
     *
     * <p><b>Note</b>: TrashOps có flow riêng để move-to-trash (FS move + Room insert) — KHÔNG
     * gọi delete() ở đây cho soft-delete. Caller (PaneViewModel) phân biệt soft vs hard tại
     * layer trên.
     *
     * @throws NodeException khi source read-only hoặc IO lỗi
     */
    void delete(VirtualNode node) throws NodeException;
}
