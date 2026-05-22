package com.vpt.filemanager.operations.properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.node.source.NodeSource;

public final class PreparePropertiesModelOperationTest {
    private static final NodeSource SOURCE = new StubSource();

    @Test
    public void prepareFileModel_usesVirtualNodeFieldsAndMetadata() {
        NodePath path = NodePath.local("/sdcard/Download/a.txt");
        VirtualNode node = new VirtualNode(path, false, 12L, 1234L, SOURCE);
        PropertiesModel.PosixMetadata metadata =
                new PropertiesModel.PosixMetadata("rw-r--r-- (644)", "1000", "1000");

        PropertiesModel model = new PreparePropertiesModelOperation().execute(
                new PreparePropertiesModelOperation.Input(node, ignored -> metadata));

        assertEquals(path, model.path);
        assertEquals("a.txt", model.name);
        assertEquals("/sdcard/Download", model.parent);
        assertFalse(model.folder);
        assertEquals(12L, model.sizeBytes);
        assertEquals(1234L, model.modifiedAtMillis);
        assertEquals(metadata, model.posixMetadata);
    }

    @Test
    public void prepareRootFolderModel_usesRootParentDisplay() {
        NodePath path = NodePath.local("/");
        VirtualNode node = new VirtualNode(path, true, -1L, 0L, SOURCE);

        PropertiesModel model = new PreparePropertiesModelOperation().execute(
                new PreparePropertiesModelOperation.Input(node, ignored -> null));

        assertEquals("/", model.name);
        assertEquals("/", model.parent);
        assertTrue(model.folder);
        assertNull(model.posixMetadata);
    }

    @Test
    public void prepareArchiveModel_keepsVirtualPathAndAllowsMissingMetadata() {
        NodePath archivePath = NodePath.inArchive(NodePath.local("/sdcard/z.zip"), "/inner/a.txt");
        VirtualNode node = new VirtualNode(archivePath, false, 5L, 10L, SOURCE);

        PropertiesModel model = new PreparePropertiesModelOperation().execute(
                new PreparePropertiesModelOperation.Input(node, ignored -> null));

        assertEquals("a.txt", model.name);
        assertEquals("/inner", model.parent);
        assertNull(model.posixMetadata);
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
