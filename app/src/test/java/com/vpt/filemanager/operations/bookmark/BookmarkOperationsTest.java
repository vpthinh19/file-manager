package com.vpt.filemanager.operations.bookmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.node.source.NodeSource;

public final class BookmarkOperationsTest {
    private static final NodeSource SOURCE = new StubSource();

    @Test
    public void addBookmark_delegatesVirtualNode() throws Exception {
        VirtualNode node = folder("/sdcard/docs");
        List<VirtualNode> added = new ArrayList<>();
        AddBookmarkOperation operation = new AddBookmarkOperation(added::add);

        operation.execute(node);

        assertEquals(1, added.size());
        assertSame(node, added.get(0));
    }

    @Test
    public void addBookmark_surfacesNodeError() {
        VirtualNode node = folder("/sdcard/docs");
        AddBookmarkOperation operation = new AddBookmarkOperation(ignored -> {
            throw new NodeException("not local");
        });

        NodeException error = assertThrows(NodeException.class, () -> operation.execute(node));

        assertEquals("not local", error.getMessage());
    }

    @Test
    public void removeBookmarks_continuesAfterPerItemFailure() {
        NodePath first = NodePath.local("/sdcard/a");
        NodePath bad = NodePath.local("/sdcard/bad");
        NodePath last = NodePath.local("/sdcard/c");
        List<NodePath> removed = new ArrayList<>();
        RemoveBookmarksOperation operation = new RemoveBookmarksOperation(path -> {
            if (bad.equals(path)) {
                throw new IllegalStateException("db locked");
            }
            removed.add(path);
        });

        RemoveBookmarksOperation.Result result = operation.execute(List.of(first, bad, last));

        assertEquals(List.of(first, last), removed);
        assertEquals(2, result.batch.ok);
        assertEquals(1, result.batch.failed);
        assertEquals("db locked", result.batch.lastError);
        assertEquals("2 bookmark removed, 1 failed: db locked",
                result.batch.message("bookmark removed"));
        assertEquals(true, result.mutation.affectsListing(NodePath.BOOKMARK_ROOT));
    }

    private static VirtualNode folder(String path) {
        return new VirtualNode(NodePath.local(path), true, -1L, 1L, SOURCE);
    }

    private static final class StubSource implements NodeSource {
        @Override
        public VirtualNode resolve(NodePath path) throws NodeException {
            throw new NodeException("not implemented");
        }

        @Override
        public List<VirtualNode> list(VirtualNode folder) throws NodeException {
            throw new NodeException("not implemented");
        }

        @Override
        public InputStream read(VirtualNode file) throws NodeException {
            throw new NodeException("not implemented");
        }

        @Override
        public OutputStream openWrite(VirtualNode file) throws NodeException {
            throw new NodeException("not implemented");
        }

        @Override
        public boolean supportsWrite() {
            return false;
        }

        @Override
        public VirtualNode createFile(NodePath path) throws NodeException {
            throw new NodeException("not implemented");
        }

        @Override
        public VirtualNode createFolder(NodePath path) throws NodeException {
            throw new NodeException("not implemented");
        }

        @Override
        public VirtualNode rename(VirtualNode node, String newName) throws NodeException {
            throw new NodeException("not implemented");
        }

        @Override
        public void delete(VirtualNode node) throws NodeException {
            throw new NodeException("not implemented");
        }
    }
}
