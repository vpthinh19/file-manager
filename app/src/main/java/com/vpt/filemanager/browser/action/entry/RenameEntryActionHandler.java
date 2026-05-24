package com.vpt.filemanager.browser.action.entry;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.error.NameConflictException;
import com.vpt.filemanager.data.local.LocalStorageAdapter;
import com.vpt.filemanager.data.archive.ArchiveRepository;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class RenameEntryActionHandler implements ActionHandler<RenameEntryAction> {
    private final LocalStorageAdapter files;
    private final ArchiveRepository archives;
    @Inject public RenameEntryActionHandler(LocalStorageAdapter files, ArchiveRepository archives) {
        this.files = files;
        this.archives = archives;
    }

    @Override
    public ActionResult handle(RenameEntryAction action, WorkspaceSnapshot state)
            throws FileOperationException {
        String name = action.name() == null ? "" : action.name().trim();
        if (name.isEmpty() || name.contains("/") || name.contains("\\")) {
            throw new FileOperationException("Invalid name");
        }
        if (action.item().isArchiveEntry()) {
            archives.rename(action.item(), name);
            return renamed(action);
        }
        if (!action.item().isLocalActionTarget()) throw new FileOperationException("Item cannot be renamed");
        String source = action.item().localPath();
        String parent = parent(source);
        if (files.exists(parent + "/" + name)) throw new NameConflictException(name);
        files.rename(source, name);
        return renamed(action);
    }

    private static ActionResult renamed(RenameEntryAction action) {
        return new ActionResult.Composite(java.util.List.of(
                new ActionResult.ClearSelection(action.pane(), true),
                new ActionResult.RefreshVisible("Renamed")));
    }

    private static String parent(String path) {
        int separator = path.lastIndexOf('/');
        return separator <= 0 ? "/" : path.substring(0, separator);
    }
}
