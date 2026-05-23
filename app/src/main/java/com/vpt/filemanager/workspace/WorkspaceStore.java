package com.vpt.filemanager.workspace;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodeFactory;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.node.source.RootSource;

/**
 * Workspace-owned store for live directory snapshots and mutation invalidation.
 *
 * <p>Only locations retained by panes or future sessions remain materialized. Paths in navigation
 * history are addresses only; revisiting an evicted location loads fresh children from its source.
 */
@Singleton
public final class WorkspaceStore {
    private final NodeFactory nodeFactory;
    private final WorkspaceFileWatcher fileWatcher;
    private final VirtualNode rootNode;
    private final MutableLiveData<MutationResult> mutations = new MutableLiveData<>();
    private final Map<NodePath, DirectorySnapshot> snapshots = new HashMap<>();
    private final Map<NodePath, Integer> retainCounts = new HashMap<>();
    private final Map<NodePath, Long> invalidatedAt = new HashMap<>();
    private long revision;

    @Inject
    public WorkspaceStore(NodeFactory nodeFactory,
                          WorkspaceFileWatcher fileWatcher,
                          RootSource rootSource) {
        this.nodeFactory = nodeFactory;
        this.fileWatcher = fileWatcher;
        this.rootNode = rootSource.rootNode();
        fileWatcher.setListener(this::publish);
    }

    /** Permanent logical entry point of the virtual workspace tree. */
    @NonNull
    public VirtualNode rootNode() {
        return rootNode;
    }

    public LiveData<MutationResult> mutations() {
        return mutations;
    }

    public synchronized void retain(@NonNull NodePath path) {
        retainCounts.merge(path, 1, Integer::sum);
        fileWatcher.retain(path);
    }

    public synchronized void release(@NonNull NodePath path) {
        fileWatcher.release(path);
        Integer count = retainCounts.get(path);
        if (count == null || count <= 1) {
            retainCounts.remove(path);
            snapshots.remove(path);
            invalidatedAt.remove(path);
        } else {
            retainCounts.put(path, count - 1);
        }
    }

    /**
     * Reads a fresh snapshot when navigating or explicitly refreshing a pane.
     */
    @NonNull
    public synchronized DirectorySnapshot reload(@NonNull NodePath path) throws NodeException {
        return materialize(path);
    }

    /**
     * Reads after a mutation only if no other pane has already reconciled this live path.
     */
    @NonNull
    public synchronized DirectorySnapshot reconcile(@NonNull NodePath path) throws NodeException {
        DirectorySnapshot current = snapshots.get(path);
        Long requiredRevision = invalidatedAt.get(path);
        if (current != null
                && (requiredRevision == null || current.revision >= requiredRevision)) {
            return current;
        }
        return materialize(path);
    }

    /**
     * Invalidates live materialized branches and publishes one workspace mutation event.
     */
    public void publish(@NonNull MutationResult mutation) {
        invalidateSnapshots(mutation);
        mutations.postValue(mutation);
    }

    synchronized void invalidateSnapshots(@NonNull MutationResult mutation) {
        revision++;
        for (NodePath path : retainCounts.keySet()) {
            if (mutation.affectsListing(path)) {
                invalidatedAt.put(path, revision);
            }
        }
    }

    private DirectorySnapshot materialize(NodePath path) throws NodeException {
        VirtualNode container = nodeFactory.fromPath(path);
        DirectorySnapshot snapshot = new DirectorySnapshot(path, ++revision, container.children());
        if (retainCounts.containsKey(path)) {
            snapshots.put(path, snapshot);
            invalidatedAt.remove(path);
        }
        return snapshot;
    }
}
