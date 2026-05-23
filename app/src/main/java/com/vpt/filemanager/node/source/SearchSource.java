package com.vpt.filemanager.node.source;

import androidx.annotation.NonNull;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodeFactory;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Source for a transient {@code search://} result container.
 *
 * <p>The result node retains only scope and query in its path. Listing re-traverses the virtual
 * source, so workspace reconciliation renders current nodes without maintaining a second tree.
 */
@Singleton
public final class SearchSource implements NodeSource {
    static final int MAX_RESULTS = 1000;
    static final int MAX_VISITED_CONTAINERS = 10000;

    private final Provider<NodeFactory> nodeFactory;

    @Inject
    public SearchSource(Provider<NodeFactory> nodeFactory) {
        this.nodeFactory = nodeFactory;
    }

    @Override
    public VirtualNode resolve(NodePath path) throws NodeException {
        requireSearchRoot(path);
        return new VirtualNode(path, true, -1L, -1L, this);
    }

    @Override
    public List<VirtualNode> list(VirtualNode folder) throws NodeException {
        NodePath searchPath = folder.path();
        requireSearchRoot(searchPath);
        String query = searchPath.searchQuery().toLowerCase(Locale.ROOT);
        if (query.isBlank()) {
            return List.of();
        }
        VirtualNode scope = nodeFactory.get().fromPath(searchPath.searchScope());
        if (!scope.isFolder()) {
            return nameMatches(scope, query) ? List.of(scope) : List.of();
        }

        List<VirtualNode> results = new ArrayList<>();
        ArrayDeque<VirtualNode> pending = new ArrayDeque<>();
        Set<NodePath> visited = new HashSet<>();
        pending.add(scope);
        while (!pending.isEmpty()
                && visited.size() < MAX_VISITED_CONTAINERS
                && results.size() < MAX_RESULTS) {
            if (Thread.currentThread().isInterrupted()) {
                throw new NodeException("Search cancelled");
            }
            VirtualNode container = pending.removeFirst();
            if (!visited.add(container.path())) {
                continue;
            }
            List<VirtualNode> children;
            try {
                children = container.children();
            } catch (NodeException inaccessibleBranch) {
                if (container.path().equals(scope.path())) {
                    throw inaccessibleBranch;
                }
                continue;
            }
            for (VirtualNode child : children) {
                if (nameMatches(child, query)) {
                    results.add(child);
                    if (results.size() >= MAX_RESULTS) {
                        break;
                    }
                }
                if (child.isFolder()) {
                    pending.addLast(child);
                }
            }
        }
        return List.copyOf(results);
    }

    @Override
    public InputStream read(VirtualNode file) throws NodeException {
        throw new NodeException("Search result container cannot be read");
    }

    @Override
    public OutputStream openWrite(VirtualNode file) throws NodeException {
        throw new NodeException("Search result container is read-only");
    }

    @Override
    public boolean supportsWrite() {
        return false;
    }

    @Override
    public VirtualNode createFile(NodePath path) throws NodeException {
        throw new NodeException("Cannot create inside search results");
    }

    @Override
    public VirtualNode createFolder(NodePath path) throws NodeException {
        throw new NodeException("Cannot create inside search results");
    }

    @Override
    public VirtualNode rename(VirtualNode node, String newName) throws NodeException {
        throw new NodeException("Cannot rename the search result container");
    }

    @Override
    public void delete(VirtualNode node) throws NodeException {
        throw new NodeException("Cannot delete the search result container");
    }

    private static boolean nameMatches(@NonNull VirtualNode node, @NonNull String query) {
        return node.name().toLowerCase(Locale.ROOT).contains(query);
    }

    private static void requireSearchRoot(@NonNull NodePath path) throws NodeException {
        if (!path.isSearch() || !"/".equals(path.path())) {
            throw new NodeException("SearchSource only resolves search result roots");
        }
        try {
            path.searchScope();
            path.searchQuery();
        } catch (IllegalStateException malformed) {
            throw new NodeException("Invalid search result path", malformed);
        }
    }
}
