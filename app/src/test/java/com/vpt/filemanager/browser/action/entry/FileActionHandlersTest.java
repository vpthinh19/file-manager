package com.vpt.filemanager.browser.action.entry;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vpt.filemanager.browser.action.browse.SortOrder;
import com.vpt.filemanager.browser.action.transfer.CancellationToken;
import com.vpt.filemanager.browser.action.transfer.TransferConflictDecision;
import com.vpt.filemanager.browser.action.transfer.TransferEntriesAction;
import com.vpt.filemanager.browser.action.transfer.TransferEntriesHandler;
import com.vpt.filemanager.browser.action.transfer.TransferMode;
import com.vpt.filemanager.browser.item.ItemFactory;
import com.vpt.filemanager.browser.item.Path;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.data.local.LocalStorageAdapter;
import com.vpt.filemanager.browser.workspace.state.PaneId;
import com.vpt.filemanager.browser.workspace.state.PaneState;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class FileActionHandlersTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private final LocalStorageAdapter files = new LocalStorageAdapter(new ItemFactory());

    @Test
    public void createAndRenameHandlersChangePhysicalStorage() throws Exception {
        java.nio.file.Path left = temporaryFolder.newFolder("left").toPath();
        WorkspaceSnapshot state = workspace(left, temporaryFolder.newFolder("right").toPath());
        new CreateEntryActionHandler(files, null, null).handle(new CreateEntryAction(PaneId.LEFT,
                CreateEntryAction.Type.FILE, "draft.txt", ExistingNamePolicy.FAIL), state);
        Item draft = files.inspect(path(left.resolve("draft.txt")));
        new RenameEntryActionHandler(files, null).handle(new RenameEntryAction(PaneId.LEFT, draft, "final.txt"), state);
        assertTrue(Files.exists(left.resolve("final.txt")));
    }

    @Test
    public void transferHandlerCopiesIntoInactivePaneDestination() throws Exception {
        java.nio.file.Path left = temporaryFolder.newFolder("left").toPath();
        java.nio.file.Path right = temporaryFolder.newFolder("right").toPath();
        Files.write(left.resolve("note.txt"), "content".getBytes(StandardCharsets.UTF_8));
        Item source = files.inspect(path(left.resolve("note.txt")));
        new TransferEntriesHandler(files, null, null).handle(new TransferEntriesAction(PaneId.LEFT,
                PaneId.RIGHT, List.of(source), TransferMode.COPY,
                (existing, name) -> TransferConflictDecision.CANCEL, new CancellationToken()),
                workspace(left, right));
        assertTrue(Files.exists(right.resolve("note.txt")));
    }

    private static WorkspaceSnapshot workspace(java.nio.file.Path left, java.nio.file.Path right) {
        PaneState source = new PaneState(Path.storage(path(left)), List.of(), Set.of(),
                SortOrder.DEFAULT, false, null, false, false, false, 0, 0, 0, 0);
        PaneState destination = new PaneState(Path.storage(path(right)), List.of(), Set.of(),
                SortOrder.DEFAULT, false, null, false, false, false, 0, 0, 0, 0);
        return new WorkspaceSnapshot(source, destination, PaneId.LEFT);
    }

    private static String path(java.nio.file.Path value) {
        return value.toString().replace('\\', '/');
    }
}
