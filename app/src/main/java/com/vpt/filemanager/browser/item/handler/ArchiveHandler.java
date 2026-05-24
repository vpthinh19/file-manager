package com.vpt.filemanager.browser.item.handler;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.item.Path;
import com.vpt.filemanager.browser.workspace.effect.WorkspaceEffect;
import com.vpt.filemanager.browser.workspace.state.PaneId;

/** Opens a physical archive as an ephemeral pane location. */
public final class ArchiveHandler implements ItemHandler {
    @Inject public ArchiveHandler() {}
    @Override public ActionResult activate(PaneId pane, Item item) {
        if (item.isArchiveEntry()) {
            return new ActionResult.Effect(new WorkspaceEffect.Toast(
                    "Nested archive editing is not available yet"));
        }
        return new ActionResult.Navigate(pane, Path.archive(item.localPath(), "/"));
    }
}
