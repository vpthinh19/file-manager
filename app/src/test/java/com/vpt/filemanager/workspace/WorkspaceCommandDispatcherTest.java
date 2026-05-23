package com.vpt.filemanager.workspace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import com.vpt.filemanager.data.db.dao.TrashDao;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.node.source.LocalSource;
import com.vpt.filemanager.operations.create.CreateNodeOperation;
import com.vpt.filemanager.operations.create.CreateNodeType;
import com.vpt.filemanager.operations.create.ExistingNamePolicy;
import com.vpt.filemanager.operations.support.NodeFileBackend;
import com.vpt.filemanager.operations.transfer.TransferConflictDecision;
import com.vpt.filemanager.operations.transfer.TransferKind;
import com.vpt.filemanager.operations.transfer.TransferOperation;
import com.vpt.filemanager.operations.trash.TrashStore;
import com.vpt.filemanager.rules.RuleEngine;
import com.vpt.filemanager.rules.WorkspaceRuleState;

public final class WorkspaceCommandDispatcherTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private WorkspaceStore store;
    private LocalSource source;
    private CreateNodeOperation createOperation;
    private TransferOperation transferOperation;
    private Path root;

    @Before
    public void setUp() {
        store = mock(WorkspaceStore.class);
        source = new LocalSource();
        TrashStore trashStore = new TrashStore(mock(TrashDao.class));
        createOperation = new CreateNodeOperation(new NodeFileBackend(), trashStore);
        transferOperation = new TransferOperation(new NodeFileBackend(), trashStore);
        root = temp.getRoot().toPath();
    }

    @Test
    public void create_allowedCommandPublishesOperationMutation() throws Exception {
        VirtualNode parent = source.resolve(path(root));
        WorkspaceCommandDispatcher dispatcher = dispatcher();

        CreateNodeOperation.Result result = dispatcher.create(
                new CreateNodeOperation.Input(parent, CreateNodeType.FILE, "note.txt",
                        ExistingNamePolicy.FAIL),
                WorkspaceRuleState.of(Collections.emptySet(), null, parent.path(),
                        NodePath.local("/different")));

        ArgumentCaptor<MutationResult> published = ArgumentCaptor.forClass(MutationResult.class);
        verify(store).publish(published.capture());
        assertEquals(result.mutation, published.getValue());
        assertTrue(published.getValue().affectsListing(parent.path()));
    }

    @Test
    public void create_atVirtualRootIsRejectedBeforeOperationRuns() throws Exception {
        VirtualNode localParent = source.resolve(path(root));
        WorkspaceCommandDispatcher dispatcher = dispatcher();

        assertThrows(NodeException.class, () -> dispatcher.create(
                new CreateNodeOperation.Input(localParent, CreateNodeType.FILE, "blocked.txt",
                        ExistingNamePolicy.FAIL),
                WorkspaceRuleState.of(Collections.emptySet(), null, NodePath.ROOT, null)));

        assertTrue(Files.notExists(root.resolve("blocked.txt")));
        verify(store, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void transfer_betweenSamePanePathsIsRejectedBeforeCopy() throws Exception {
        Path sourcePath = Files.write(root.resolve("a.txt"), "A".getBytes(StandardCharsets.UTF_8));
        Path destination = Files.createDirectory(root.resolve("dst"));
        VirtualNode sourceNode = source.resolve(path(sourcePath));
        VirtualNode destinationNode = source.resolve(path(destination));
        WorkspaceCommandDispatcher dispatcher = dispatcher();

        assertThrows(NodeException.class, () -> dispatcher.transfer(
                new TransferOperation.Input(List.of(sourceNode), destinationNode, TransferKind.COPY,
                        ignored -> TransferConflictDecision.CANCEL,
                        NodeFileBackend.CancellationToken.neverCancelled()),
                WorkspaceRuleState.of(Collections.singleton(sourceNode.path()), Boolean.FALSE,
                        destinationNode.path(), destinationNode.path())));

        assertTrue(Files.notExists(destination.resolve("a.txt")));
        verify(store, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    private WorkspaceCommandDispatcher dispatcher() {
        return new WorkspaceCommandDispatcher(store, createOperation, null, null,
                transferOperation, null, null, null, null, RuleEngine.defaults());
    }

    private static NodePath path(Path nio) {
        return NodePath.local(nio.toString().replace('\\', '/'));
    }
}
