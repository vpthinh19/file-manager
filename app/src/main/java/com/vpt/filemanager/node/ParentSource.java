package com.vpt.filemanager.node;

import java.io.InputStream;
import java.util.List;

import com.vpt.filemanager.node.FilePath;
import com.vpt.filemanager.node.source.NodeSource;

/**
 * Sentinel {@link NodeSource} cho ".." parent marker row trong adapter. KHÔNG có content thật —
 * mọi method đều throw nếu được gọi. Caller (adapter / view holder / PaneViewModel) phát hiện
 * marker qua {@link VirtualNode#isParent()} và xử lý đặc biệt (navigate up, không vào selection).
 *
 * <p>Package-private + singleton — chỉ {@link VirtualNode#parent(FilePath)} factory tạo node
 * dùng source này. Detect qua {@code instanceof ParentSource} trong {@link VirtualNode}.
 */
final class ParentSource implements NodeSource {
    static final ParentSource INSTANCE = new ParentSource();

    private ParentSource() {
    }

    @Override
    public VirtualNode resolve(FilePath path) throws NodeException {
        throw new NodeException("Parent marker has no resolve");
    }

    @Override
    public List<VirtualNode> list(VirtualNode folder) throws NodeException {
        throw new NodeException("Parent marker has no children — use navigateTo(path)");
    }

    @Override
    public InputStream read(VirtualNode file) throws NodeException {
        throw new NodeException("Parent marker has no content");
    }

    @Override
    public boolean supportsWrite() {
        return false;
    }

    @Override
    public VirtualNode createFile(FilePath path) throws NodeException {
        throw new NodeException("Parent marker is read-only");
    }

    @Override
    public VirtualNode createFolder(FilePath path) throws NodeException {
        throw new NodeException("Parent marker is read-only");
    }

    @Override
    public VirtualNode rename(VirtualNode node, String newName) throws NodeException {
        throw new NodeException("Parent marker is read-only");
    }

    @Override
    public void delete(VirtualNode node) throws NodeException {
        throw new NodeException("Parent marker is read-only");
    }
}
