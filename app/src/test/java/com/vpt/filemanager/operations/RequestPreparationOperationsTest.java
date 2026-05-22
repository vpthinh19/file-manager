package com.vpt.filemanager.operations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.node.source.NodeSource;
import com.vpt.filemanager.operations.openwith.OpenWithRequest;
import com.vpt.filemanager.operations.openwith.PrepareOpenWithRequestOperation;
import com.vpt.filemanager.operations.share.PrepareShareRequestOperation;
import com.vpt.filemanager.operations.share.ShareRequest;

public final class RequestPreparationOperationsTest {
    private static final NodeSource SOURCE = new StubSource();

    @Test
    public void prepareShareRequest_keepsOnlyLocalNodes() {
        VirtualNode local = file(NodePath.local("/sdcard/a.txt"));
        VirtualNode archive = file(NodePath.inArchive(NodePath.local("/sdcard/z.zip"), "/a.txt"));

        ShareRequest request = new PrepareShareRequestOperation().execute(
                new PrepareShareRequestOperation.Input(List.of(local, archive)));

        assertEquals(List.of(local.path()), request.localPaths);
    }

    @Test
    public void prepareOpenWithRequest_detectsMimeForLocalFile() throws Exception {
        VirtualNode node = file(NodePath.local("/sdcard/a.txt"));

        OpenWithRequest request = new PrepareOpenWithRequestOperation().execute(
                new PrepareOpenWithRequestOperation.Input(node, null));

        assertEquals(node.path(), request.localPath);
        assertEquals("text/plain", request.mimeType);
    }

    @Test
    public void prepareOpenWithRequest_usesMimeOverride() throws Exception {
        VirtualNode node = file(NodePath.local("/sdcard/a.unknown"));

        OpenWithRequest request = new PrepareOpenWithRequestOperation().execute(
                new PrepareOpenWithRequestOperation.Input(node, "image/*"));

        assertEquals("image/*", request.mimeType);
    }

    @Test
    public void prepareOpenWithRequest_rejectsFolder() {
        VirtualNode folder = new VirtualNode(NodePath.local("/sdcard/folder"),
                true, -1L, 1L, SOURCE);

        assertThrows(NodeException.class, () -> new PrepareOpenWithRequestOperation().execute(
                new PrepareOpenWithRequestOperation.Input(folder, null)));
    }

    @Test
    public void prepareOpenWithRequest_rejectsNonLocalFile() {
        VirtualNode archive = file(NodePath.inArchive(NodePath.local("/sdcard/z.zip"), "/a.txt"));

        assertThrows(NodeException.class, () -> new PrepareOpenWithRequestOperation().execute(
                new PrepareOpenWithRequestOperation.Input(archive, null)));
    }

    private static VirtualNode file(NodePath path) {
        return new VirtualNode(path, false, 1L, 1L, SOURCE);
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
