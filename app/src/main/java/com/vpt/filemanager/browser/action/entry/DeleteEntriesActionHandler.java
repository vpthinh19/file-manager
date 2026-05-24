package com.vpt.filemanager.browser.action.entry;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.data.persistence.TrashStore;
import com.vpt.filemanager.data.archive.ArchiveRepository;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class DeleteEntriesActionHandler implements ActionHandler<DeleteEntriesAction> {
    private final TrashStore trash;
    private final ArchiveRepository archives;
    @Inject public DeleteEntriesActionHandler(TrashStore trash, ArchiveRepository archives) {
        this.trash = trash;
        this.archives = archives;
    }

    @Override
    public ActionResult handle(DeleteEntriesAction action, WorkspaceSnapshot state)
            throws FileOperationException {
        int count = 0;
        java.util.List<Item> archiveEntries = new java.util.ArrayList<>();
        for (Item item : action.items()) {
            if (item.isParent()) continue;
            if (item.isArchiveEntry()) archiveEntries.add(item);
            else trash.put(item);
            count++;
        }
        if (!archiveEntries.isEmpty()) archives.delete(archiveEntries);
        return new ActionResult.Composite(java.util.List.of(
                new ActionResult.ClearSelection(action.pane(), true),
                new ActionResult.RefreshVisible(count + " item(s) deleted")));
    }
}
