package com.vpt.filemanager.browser.action.open;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.workspace.effect.WorkspaceEffect;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.item.ItemFactory;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.data.archive.ArchiveRepository;

public final class OpenWithActionHandler implements ActionHandler<OpenWithAction> {
    private final ArchiveRepository archives;
    private final ItemFactory items;
    @Inject public OpenWithActionHandler(ArchiveRepository archives, ItemFactory items) {
        this.archives = archives;
        this.items = items;
    }
    @Override public ActionResult handle(OpenWithAction action, WorkspaceSnapshot state)
            throws FileOperationException {
        Item item = action.item();
        if (item.isArchiveEntry()) {
            String path = archives.materialize(item);
            item = items.local(path, item.name(), false, item.size(), item.modifiedAt());
        }
        return new ActionResult.Effect(new WorkspaceEffect.OpenExternal(item));
    }
}
