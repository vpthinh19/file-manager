package com.vpt.filemanager.browser.item.handler;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.workspace.effect.WorkspaceEffect;
import com.vpt.filemanager.browser.workspace.state.PaneId;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.data.archive.ArchiveRepository;

public final class TextHandler implements ItemHandler {
    private final ArchiveRepository archives;
    @Inject public TextHandler(ArchiveRepository archives) { this.archives = archives; }
    @Override public ActionResult activate(PaneId pane, Item item) throws FileOperationException {
        boolean extracted = item.isArchiveEntry();
        String path = extracted ? archives.materialize(item) : item.localPath();
        boolean readOnly = extracted && !archives.canWrite(item.archiveEntry());
        return new ActionResult.Effect(new WorkspaceEffect.OpenText(path, item.name(), readOnly,
                extracted ? item.archiveEntry().serialize() : null));
    }
}
