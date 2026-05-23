package com.vpt.filemanager.workspace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodeFactory;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.node.source.NodeSource;
import com.vpt.filemanager.node.source.RootSource;

public final class WorkspaceStoreTest {
    private static final NodePath DOCUMENTS = NodePath.local("/sdcard/Documents");

    private CountingSource source;
    private NodeFactory factory;
    private WorkspaceFileWatcher watcher;
    private WorkspaceStore store;

    @Before
    public void setUp() throws Exception {
        source = new CountingSource();
        factory = mock(NodeFactory.class);
        watcher = mock(WorkspaceFileWatcher.class);
        when(factory.fromPath(DOCUMENTS))
                .thenReturn(new VirtualNode(DOCUMENTS, true, -1L, 0L, source));
        RootSource rootSource = mock(RootSource.class);
        when(rootSource.rootNode()).thenReturn(
                new VirtualNode(NodePath.ROOT, true, -1L, -1L, source));
        store = new WorkspaceStore(factory, watcher, rootSource);
    }

    @Test
    public void retainAndRelease_delegateLiveWatchOwnership() {
        store.retain(DOCUMENTS);
        store.release(DOCUMENTS);

        verify(watcher).retain(DOCUMENTS);
        verify(watcher).release(DOCUMENTS);
    }

    @Test
    public void documentSession_retainsAndReleasesItsParentWatch() {
        NodePath document = DOCUMENTS.child("note.txt");

        DocumentSession session = store.openDocument(document);
        session.close();

        verify(watcher).retain(DOCUMENTS);
        verify(watcher).release(DOCUMENTS);
    }

    @Test
    public void searchSnapshot_observesOriginalLocalScope() {
        NodePath search = NodePath.search(DOCUMENTS, "note");

        store.retain(search);
        store.release(search);

        verify(watcher).retain(DOCUMENTS);
        verify(watcher).release(DOCUMENTS);
    }

    @Test
    public void archiveSnapshot_observesPhysicalContainerParent() {
        NodePath archive = NodePath.inArchive(DOCUMENTS.child("data.zip"), "/docs");

        store.retain(archive);
        store.release(archive);

        verify(watcher).retain(DOCUMENTS);
        verify(watcher).release(DOCUMENTS);
    }

    @Test
    public void rootNode_isStableLogicalTreeEntryPoint() {
        assertSame(store.rootNode(), store.rootNode());
        assertEquals(NodePath.ROOT, store.rootNode().path());
    }

    @Test
    public void twoPanesReconcileOneInvalidatedPath_withOneReload() throws Exception {
        store.retain(DOCUMENTS);
        store.retain(DOCUMENTS);
        store.reload(DOCUMENTS);
        assertEquals(1, source.listCalls);

        store.invalidateSnapshots(MutationResult.builder().changedContainer(DOCUMENTS).build());
        DirectorySnapshot firstPane = store.reconcile(DOCUMENTS);
        DirectorySnapshot secondPane = store.reconcile(DOCUMENTS);

        assertEquals(2, source.listCalls);
        assertEquals(firstPane.revision, secondPane.revision);
    }

    @Test
    public void twoPanesOpeningSamePath_shareCurrentMaterializedSnapshot() throws Exception {
        store.retain(DOCUMENTS);
        store.retain(DOCUMENTS);

        DirectorySnapshot firstPane = store.open(DOCUMENTS);
        DirectorySnapshot secondPane = store.open(DOCUMENTS);

        assertEquals(1, source.listCalls);
        assertSame(firstPane, secondPane);
    }

    @Test
    public void releasedSnapshot_isNotReusedWhenPathIsOpenedAgain() throws Exception {
        store.retain(DOCUMENTS);
        store.reload(DOCUMENTS);
        store.release(DOCUMENTS);
        store.retain(DOCUMENTS);

        store.reconcile(DOCUMENTS);

        assertEquals(2, source.listCalls);
    }

    private static final class CountingSource implements NodeSource {
        int listCalls;

        @Override
        public VirtualNode resolve(NodePath path) throws NodeException {
            throw new NodeException("unused");
        }

        @Override
        public List<VirtualNode> list(VirtualNode folder) {
            listCalls++;
            return Collections.emptyList();
        }

        @Override
        public InputStream read(VirtualNode file) throws NodeException {
            throw new NodeException("unused");
        }

        @Override
        public OutputStream openWrite(VirtualNode file) throws NodeException {
            throw new NodeException("unused");
        }

        @Override
        public boolean supportsWrite() {
            return false;
        }

        @Override
        public VirtualNode createFile(NodePath path) throws NodeException {
            throw new NodeException("unused");
        }

        @Override
        public VirtualNode createFolder(NodePath path) throws NodeException {
            throw new NodeException("unused");
        }

        @Override
        public VirtualNode rename(VirtualNode node, String newName) throws NodeException {
            throw new NodeException("unused");
        }

        @Override
        public void delete(VirtualNode node) throws NodeException {
            throw new NodeException("unused");
        }
    }
}
