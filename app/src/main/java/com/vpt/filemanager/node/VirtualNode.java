package com.vpt.filemanager.node;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.source.NodeSource;
import com.vpt.filemanager.node.source.ParentSource;

/**
 * Phần tử trung tâm của DOM ảo. Một {@code VirtualNode} đại diện cho một "thứ user có thể nhìn
 * thấy trên 1 hàng" — folder local, file local, folder ảo trong archive, entry trong archive,
 * root ảo của Trash / Bookmark, hay bất kỳ nguồn tương lai (SMB, cloud, ...).
 *
 * <h2>Hai trục thiết kế orthogonal</h2>
 * <ul>
 *   <li><b>NGUỒN</b> (where) — ẩn trong {@link NodeSource}. UI không quan tâm.</li>
 *   <li><b>HÀNH VI khi click</b> (what) — handled bởi {@code NodeOpener} (Phase R-3).</li>
 * </ul>
 * Cùng 1 file có thể được wrap bằng nhiều source khác nhau ở thời điểm khác nhau (ví dụ:
 * {@code photos.zip} là {@link com.vpt.filemanager.node.NodePath} local thông thường khi
 * user thấy nó trong folder; khi user click thì {@code ArchiveOpener} re-wrap nó thành VirtualNode
 * có {@code source=ArchiveSource} để navigate vào).
 *
 * <h2>Immutability + memory</h2>
 * <p>Class {@code final}, mọi field {@code final}. Hash + equals dựa duy nhất trên path → có thể
 * dùng làm Set/Map key cho selection tracking. {@link NodeSource} là singleton (LocalSource /
 * TrashSource / BookmarkSource), nên 10.000 node trong 1 folder share cùng 1 reference source —
 * không waste RAM.
 *
 * <h2>Lazy children</h2>
 * <p>{@link #children()} KHÔNG cache — mỗi lần gọi đều delegate xuống source. Caller (PaneViewModel)
 * chịu trách nhiệm cache snapshot ở UI layer + invalidate khi navigate.
 */
public final class VirtualNode {
    private final NodePath path;
    private final boolean isFolder;
    private final long size;
    private final long modifiedAt;
    private final NodeSource source;

    public VirtualNode(NodePath path, boolean isFolder, long size, long modifiedAt, NodeSource source) {
        this.path = Objects.requireNonNull(path, "path");
        this.isFolder = isFolder;
        this.size = size;
        this.modifiedAt = modifiedAt;
        this.source = Objects.requireNonNull(source, "source");
    }

    /**
     * Factory cho synthetic ".." parent marker row. {@link #path()} trỏ tới parent path; click
     * marker → caller navigate đến path đó. Source là {@link ParentSource} singleton; mọi attempt
     * children/read/write trên marker đều throw — caller phải check {@link #isParent()} trước.
     */
    public static VirtualNode parent(NodePath parentPath) {
        return new VirtualNode(parentPath, true, -1L, -1L, ParentSource.INSTANCE);
    }

    /**
     * {@code true} nếu node này là synthetic parent marker (".." row). Adapter / ViewHolder dùng
     * để render tên "..", PaneViewModel dùng để skip trong selection.
     */
    public boolean isParent() {
        return source instanceof ParentSource;
    }

    public NodePath path() {
        return path;
    }

    /** Tên hiển thị (derived từ path). Không cache field riêng để tiết kiệm RAM. */
    public String name() {
        return path.name();
    }

    public boolean isFolder() {
        return isFolder;
    }

    public long size() {
        return size;
    }

    public long modifiedAt() {
        return modifiedAt;
    }

    public NodeSource source() {
        return source;
    }

    /**
     * List children — chỉ gọi cho folder. Delegate xuống {@link NodeSource#list(VirtualNode)}.
     *
     * @throws NodeException khi không phải folder, hoặc khi source lỗi (xem doc của source impl)
     */
    public List<VirtualNode> children() throws NodeException {
        if (!isFolder) {
            throw new NodeException("Not a folder: " + path);
        }
        return source.list(this);
    }

    /**
     * Mở stream đọc — chỉ gọi cho file. Delegate xuống {@link NodeSource#read(VirtualNode)}.
     *
     * @throws NodeException khi đây là folder, hoặc khi source lỗi
     */
    public InputStream openRead() throws NodeException {
        if (isFolder) {
            throw new NodeException("Cannot read a folder: " + path);
        }
        return source.read(this);
    }

    /**
     * Mở stream ghi — chỉ gọi cho file đã tồn tại (caller phải tạo file trước qua
     * {@code source().createFile(path)}). Truncate existing nội dung. Symmetric với {@link #openRead}.
     *
     * @throws NodeException khi đây là folder, source read-only, hoặc IO lỗi
     */
    public OutputStream openWrite() throws NodeException {
        if (isFolder) {
            throw new NodeException("Cannot write a folder: " + path);
        }
        return source.openWrite(this);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VirtualNode && path.equals(((VirtualNode) o).path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return (isFolder ? "VirtualFolder[" : "VirtualFile[") + path + "]";
    }
}
