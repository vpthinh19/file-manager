package com.vpt.filemanager.browser.item.handler;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.workspace.effect.WorkspaceEffect;
import com.vpt.filemanager.browser.workspace.state.PaneId;
import com.vpt.filemanager.browser.item.ItemFactory;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.data.archive.ArchiveRepository;

public final class ExternalHandler implements ItemHandler {
    private final ArchiveRepository archives;
    private final ItemFactory items;
    @Inject public ExternalHandler(ArchiveRepository archives, ItemFactory items) {
        this.archives = archives;
        this.items = items;
    }
    @Override public ActionResult activate(PaneId pane, Item item) throws FileOperationException {
        return new ActionResult.Effect(new WorkspaceEffect.OpenExternal(materialize(item)));
    }

    private Item materialize(Item item) throws FileOperationException {
        if (!item.isArchiveEntry()) return item;
        String path = archives.materialize(item);
        return items.local(path, item.name(), false, item.size(), item.modifiedAt());
    }
}
