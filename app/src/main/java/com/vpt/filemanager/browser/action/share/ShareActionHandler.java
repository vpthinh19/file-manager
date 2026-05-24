package com.vpt.filemanager.browser.action.share;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.workspace.effect.WorkspaceEffect;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.item.ItemFactory;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.data.archive.ArchiveRepository;
import java.util.ArrayList;
import java.util.List;

public final class ShareActionHandler implements ActionHandler<ShareAction> {
    private final ArchiveRepository archives;
    private final ItemFactory items;
    @Inject public ShareActionHandler(ArchiveRepository archives, ItemFactory items) {
        this.archives = archives;
        this.items = items;
    }
    @Override public ActionResult handle(ShareAction action, WorkspaceSnapshot state)
            throws FileOperationException {
        List<Item> shareable = new ArrayList<>(action.items().size());
        for (Item item : action.items()) {
            if (item.isArchiveEntry() && !item.isFolder()) {
                String path = archives.materialize(item);
                shareable.add(items.local(path, item.name(), false, item.size(), item.modifiedAt()));
            } else {
                shareable.add(item);
            }
        }
        return new ActionResult.Composite(java.util.List.of(
                new ActionResult.Effect(new WorkspaceEffect.Share(shareable)),
                new ActionResult.ClearSelection(action.pane(), true)));
    }
}
