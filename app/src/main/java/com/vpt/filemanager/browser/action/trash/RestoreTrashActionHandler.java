package com.vpt.filemanager.browser.action.trash;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.data.persistence.TrashStore;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class RestoreTrashActionHandler implements ActionHandler<RestoreTrashAction> {
    private final TrashStore trash;
    @Inject public RestoreTrashActionHandler(TrashStore trash) { this.trash = trash; }
    @Override public ActionResult handle(RestoreTrashAction action, WorkspaceSnapshot state)
            throws FileOperationException {
        for (Item item : action.items()) {
            if (item.recordId() != null) trash.restore(item.recordId());
        }
        return new ActionResult.Composite(java.util.List.of(
                new ActionResult.ClearSelection(action.pane(), true),
                new ActionResult.RefreshVisible("Restored")));
    }
}
