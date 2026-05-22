package com.vpt.filemanager.operations;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.operations.navigation.NavigateToParentOperation;
import com.vpt.filemanager.operations.pane.SwapActivePaneOperation;
import com.vpt.filemanager.operations.selection.SelectRangeOperation;
import com.vpt.filemanager.operations.sort.SortNodesOperation;
import com.vpt.filemanager.operations.sort.SortOrder;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.node.source.NodeSource;

public final class WorkspaceOperationsTest {
    private static final NodeSource SOURCE = new StubSource();

    @Test
    public void navigateToParent_archiveRoot_returnsArchiveFileParent() {
        NodePath zip = NodePath.local("/sdcard/Download/a.zip");
        NavigateToParentOperation.Output output = new NavigateToParentOperation().execute(
                new NavigateToParentOperation.Input(NodePath.inArchive(zip, "/")));

        assertEquals(NodePath.local("/sdcard/Download"), output.parentPath);
    }

    @Test
    public void selectRange_fillsVisibleGapBetweenSelectedNodes() {
        VirtualNode a = file("/sdcard/a.txt");
        VirtualNode b = file("/sdcard/b.txt");
        VirtualNode c = file("/sdcard/c.txt");

        SelectRangeOperation.Output output = new SelectRangeOperation().execute(
                new SelectRangeOperation.Input(
                        Set.of(a.path(), c.path()),
                        List.of(a, b, c)));

        assertEquals(Set.of(a.path(), b.path(), c.path()), output.selection);
    }

    @Test
    public void sortNodes_sortsFolderFirstByName() {
        VirtualNode zFile = file("/sdcard/z.txt");
        VirtualNode aFolder = folder("/sdcard/a");

        SortNodesOperation.Output output = new SortNodesOperation().execute(
                new SortNodesOperation.Input(List.of(zFile, aFolder), SortOrder.NAME_ASC));

        assertEquals(aFolder.path(), output.nodes.get(0).path());
        assertEquals(zFile.path(), output.nodes.get(1).path());
    }

    @Test
    public void swapActivePane_returnsOppositePane() {
        SwapActivePaneOperation.Output output = new SwapActivePaneOperation().execute(
                new SwapActivePaneOperation.Input("left", "right", "left"));

        assertEquals("right", output.activePaneId);
    }

    private static VirtualNode file(String path) {
        return new VirtualNode(NodePath.local(path), false, 1L, 1L, SOURCE);
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
