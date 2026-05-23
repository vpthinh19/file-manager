package com.vpt.filemanager.node.source;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodeFactory;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;

public final class SearchSourceTest {
    @Test
    public void listing_searchesNestedVirtualNodesAndReturnsOriginalNodes() throws Exception {
        TreeSource tree = new TreeSource();
        VirtualNode scope = tree.folder("/scope");
        VirtualNode nested = tree.folder("/scope/nested");
        VirtualNode matchingFolder = tree.folder("/scope/Report drafts");
        VirtualNode matchingFile = tree.file("/scope/nested/report-final.txt");
        tree.children(scope, List.of(nested, matchingFolder));
        tree.children(nested, List.of(matchingFile));
        tree.children(matchingFolder, List.of());

        NodeFactory factory = mock(NodeFactory.class);
        when(factory.fromPath(scope.path())).thenReturn(scope);
        SearchSource source = new SearchSource(() -> factory);
        VirtualNode results = source.resolve(NodePath.search(scope.path(), "REPORT"));

        List<VirtualNode> found = source.list(results);

        assertEquals(List.of(matchingFolder, matchingFile), found);
        assertEquals(NodePath.local("/scope/nested/report-final.txt"), found.get(1).path());
    }

    private static final class TreeSource implements NodeSource {
        private final Map<NodePath, List<VirtualNode>> children = new HashMap<>();

        VirtualNode folder(String path) {
            return new VirtualNode(NodePath.local(path), true, -1L, 0L, this);
        }

        VirtualNode file(String path) {
            return new VirtualNode(NodePath.local(path), false, 1L, 0L, this);
        }

        void children(VirtualNode folder, List<VirtualNode> nodes) {
            children.put(folder.path(), nodes);
        }

        @Override
        public VirtualNode resolve(NodePath path) throws NodeException {
            throw new NodeException("unused");
        }

        @Override
        public List<VirtualNode> list(VirtualNode folder) {
            return children.getOrDefault(folder.path(), List.of());
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
