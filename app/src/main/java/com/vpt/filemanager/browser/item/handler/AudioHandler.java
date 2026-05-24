package com.vpt.filemanager.browser.item.handler;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.workspace.effect.WorkspaceEffect;
import com.vpt.filemanager.browser.workspace.state.PaneId;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.data.archive.ArchiveRepository;

public final class AudioHandler implements ItemHandler {
    private final ArchiveRepository archives;
    @Inject public AudioHandler(ArchiveRepository archives) { this.archives = archives; }

    @Override
    public ActionResult activate(PaneId pane, Item item) throws FileOperationException {
        String path = item.isArchiveEntry() ? archives.materialize(item) : item.localPath();
        return new ActionResult.Effect(new WorkspaceEffect.OpenMedia(path, false));
    }
}
