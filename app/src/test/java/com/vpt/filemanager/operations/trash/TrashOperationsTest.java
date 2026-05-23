package com.vpt.filemanager.operations.trash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.vpt.filemanager.node.NodeException;

public final class TrashOperationsTest {
    @Test
    public void restoreTrashEntries_continuesAfterPerItemFailure() {
        List<String> restored = new ArrayList<>();
        RestoreTrashEntriesOperation operation = new RestoreTrashEntriesOperation(entryId -> {
            if ("bad".equals(entryId)) {
                throw new NodeException("blocked");
            }
            restored.add(entryId);
        });

        RestoreTrashEntriesOperation.Result result = operation.execute(List.of("a", "bad", "b"));

        assertEquals(List.of("a", "b"), restored);
        assertEquals(2, result.batch.ok);
        assertEquals(1, result.batch.failed);
        assertEquals("blocked", result.batch.lastError);
        assertEquals("2 restored, 1 failed: blocked", result.batch.message("restored"));
        assertEquals(true, result.mutation.allLiveSnapshots);
    }

    @Test
    public void emptyTrash_delegatesToBackend() throws Exception {
        boolean[] called = { false };
        EmptyTrashOperation operation = new EmptyTrashOperation(() -> called[0] = true);

        EmptyTrashOperation.Result result = operation.execute();

        assertEquals(true, called[0]);
        assertEquals(true,
                result.mutation.affectsListing(com.vpt.filemanager.node.NodePath.TRASH_ROOT));
    }

    @Test
    public void emptyTrash_surfacesBackendError() {
        EmptyTrashOperation operation = new EmptyTrashOperation(() -> {
            throw new NodeException("cannot empty");
        });

        NodeException error = assertThrows(NodeException.class, operation::execute);

        assertEquals("cannot empty", error.getMessage());
    }
}
