package com.vpt.filemanager.node.source;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Source for the application namespace root. It materializes only the stable top-level branches;
 * descendants remain owned by their concrete sources.
 */
@Singleton
public final class RootSource implements NodeSource {
    private final LocalSource localSource;
    private final TrashSource trashSource;
    private final BookmarkSource bookmarkSource;
    private final VirtualNode rootNode;

    @Inject
    public RootSource(LocalSource localSource,
                      TrashSource trashSource,
                      BookmarkSource bookmarkSource) {
        this.localSource = localSource;
        this.trashSource = trashSource;
        this.bookmarkSource = bookmarkSource;
        this.rootNode = new VirtualNode(NodePath.ROOT, true, -1L, -1L, this);
    }

    public VirtualNode rootNode() {
        return rootNode;
    }

    @Override
    public VirtualNode resolve(NodePath path) throws NodeException {
        if (!path.equals(NodePath.ROOT)) {
            throw new NodeException("RootSource only resolves root:///");
        }
        return rootNode;
    }

    @Override
    public List<VirtualNode> list(VirtualNode folder) throws NodeException {
        if (!folder.path().equals(NodePath.ROOT)) {
            throw new NodeException("RootSource only lists root:///");
        }
        return List.of(
                localSource.resolve(NodePath.STORAGE_ROOT),
                trashSource.resolve(NodePath.TRASH_ROOT),
                bookmarkSource.resolve(NodePath.BOOKMARK_ROOT));
    }

    @Override
    public InputStream read(VirtualNode file) throws NodeException {
        throw new NodeException("Workspace root cannot be read");
    }

    @Override
    public OutputStream openWrite(VirtualNode file) throws NodeException {
        throw new NodeException("Workspace root is read-only");
    }

    @Override
    public boolean supportsWrite() {
        return false;
    }

    @Override
    public VirtualNode createFile(NodePath path) throws NodeException {
        throw new NodeException("Cannot create at workspace root");
    }

    @Override
    public VirtualNode createFolder(NodePath path) throws NodeException {
        throw new NodeException("Cannot create at workspace root");
    }

    @Override
    public VirtualNode rename(VirtualNode node, String newName) throws NodeException {
        throw new NodeException("Cannot rename a workspace root branch");
    }

    @Override
    public void delete(VirtualNode node) throws NodeException {
        throw new NodeException("Cannot delete a workspace root branch");
    }
}
