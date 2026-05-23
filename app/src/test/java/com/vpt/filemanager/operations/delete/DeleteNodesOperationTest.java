package com.vpt.filemanager.operations.delete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.Test;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.node.source.NodeSource;
import com.vpt.filemanager.operations.trash.TrashStore;

public final class DeleteNodesOperationTest {
    @Test
    public void delete_successReportsSourceTrashAndRemovedSubtree() throws Exception {
        TrashStore trashStore = mock(TrashStore.class);
        DeleteNodesOperation operation = new DeleteNodesOperation(trashStore);
        VirtualNode node = node("/sdcard/Documents/project");

        DeleteNodesOperation.Result result = operation.execute(
                new DeleteNodesOperation.Input(List.of(node), false));

        assertEquals(1, result.ok);
        assertTrue(result.mutation.affectsListing(NodePath.local("/sdcard/Documents")));
        assertTrue(result.mutation.affectsListing(NodePath.TRASH_ROOT));
        assertTrue(result.mutation.affectsListing(NodePath.local("/sdcard/Documents/project/src")));
    }

    @Test
    public void delete_partialFailureOnlyInvalidatesSucceededSubtree() throws Exception {
        TrashStore trashStore = mock(TrashStore.class);
        VirtualNode ok = node("/sdcard/Documents/ok");
        VirtualNode bad = node("/sdcard/Documents/bad");
        doThrow(new NodeException("blocked")).when(trashStore).moveToTrash(bad);
        DeleteNodesOperation operation = new DeleteNodesOperation(trashStore);

        DeleteNodesOperation.Result result = operation.execute(
                new DeleteNodesOperation.Input(List.of(ok, bad), true));

        assertEquals(1, result.ok);
        assertEquals(1, result.failed);
        assertTrue(result.mutation.affectsListing(NodePath.local("/sdcard/Documents/ok/child")));
        assertFalse(result.mutation.affectsListing(NodePath.local("/sdcard/Documents/bad/child")));
    }

    private static VirtualNode node(String path) {
        return new VirtualNode(NodePath.local(path), true, -1L, 1L, mock(NodeSource.class));
    }
}
