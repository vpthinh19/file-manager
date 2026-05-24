package com.vpt.filemanager.browser.action.trash;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.data.persistence.TrashStore;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class EmptyTrashActionHandler implements ActionHandler<EmptyTrashAction> {
    private final TrashStore trash;
    @Inject public EmptyTrashActionHandler(TrashStore trash) { this.trash = trash; }
    @Override public ActionResult handle(EmptyTrashAction action, WorkspaceSnapshot state)
            throws FileOperationException {
        trash.empty();
        return new ActionResult.RefreshVisible("Trash emptied");
    }
}
