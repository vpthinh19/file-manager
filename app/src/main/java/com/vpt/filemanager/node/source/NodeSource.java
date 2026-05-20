package com.vpt.filemanager.node.source;

import java.io.InputStream;
import java.util.List;

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
}
