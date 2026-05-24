package com.vpt.filemanager.browser.action.transfer;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.browser.item.Path;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.data.persistence.TrashStore;
import com.vpt.filemanager.data.local.LocalStorageAdapter;
import com.vpt.filemanager.data.archive.ArchiveRepository;
import com.vpt.filemanager.data.archive.ArchiveFormat;
import com.vpt.filemanager.browser.workspace.state.PaneState;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class TransferEntriesHandler implements ActionHandler<TransferEntriesAction> {
    private final LocalStorageAdapter files;
    private final ArchiveRepository archives;
    private final TrashStore trash;

    @Inject
    public TransferEntriesHandler(LocalStorageAdapter files, ArchiveRepository archives,
                                  TrashStore trash) {
        this.files = files;
        this.archives = archives;
        this.trash = trash;
    }

    @Override
    public ActionResult handle(TransferEntriesAction action, WorkspaceSnapshot state)
            throws FileOperationException {
        PaneState destinationPane = state.pane(action.destinationPane());
        Path destination = destinationPane.path;
        if (destination == null || (!destination.isStorage()
                && !(destination.isArchive() && archives.canWrite(destination)))) {
            throw new FileOperationException("Destination is not writable");
        }
        int transferred = 0;
        for (Item source : action.items()) {
            if (action.token().isCancelled()) break;
            if (source.isArchiveEntry() && destination.isArchive()) {
                throw new FileOperationException("Transfer between archive containers is not available yet");
            }
            if (!source.isArchiveEntry() && !source.isLocalActionTarget()) break;
            ResolvedName resolved = resolveName(source.name(), destination, action);
            if (action.token().isCancelled()) break;
            if (destination.isArchive()) {
                archives.importFromStorage(destination, source, resolved.name, resolved.replace);
                if (action.mode() == TransferMode.MOVE) files.deletePermanently(source.localPath());
            } else if (source.isArchiveEntry()) {
                String target = child(destination.directory(), resolved.name);
                archives.extractToStorage(source, target);
                if (action.mode() == TransferMode.MOVE) {
                    if (!ArchiveFormat.isWritable(source.archiveEntry().container())) {
                        throw new FileOperationException("This archive format is read-only");
                    }
                    archives.delete(java.util.List.of(source));
                }
            } else if (action.mode() == TransferMode.COPY) {
                files.copy(source.localPath(), child(destination.directory(), resolved.name),
                        action.token());
            } else {
                files.move(source.localPath(), child(destination.directory(), resolved.name),
                        action.token());
            }
            transferred++;
        }
        return new ActionResult.Composite(java.util.List.of(
                new ActionResult.ClearSelection(action.sourcePane(), true),
                new ActionResult.RefreshVisible(transferred + " item(s) "
                        + (action.mode() == TransferMode.COPY ? "copied" : "moved"))));
    }

    private ResolvedName resolveName(String requested, Path destination, TransferEntriesAction action)
            throws FileOperationException {
        Item existing;
        if (destination.isArchive()) {
            existing = archives.list(destination).stream()
                    .filter(item -> item.name().equals(requested)).findFirst().orElse(null);
        } else {
            String target = child(destination.directory(), requested);
            existing = files.exists(target) ? files.inspect(target) : null;
        }
        if (existing == null) return new ResolvedName(requested, false);
        TransferConflictDecision decision = action.conflicts().resolve(existing, requested);
        if (decision == TransferConflictDecision.CANCEL) {
            action.token().cancel();
            return new ResolvedName(requested, false);
        }
        if (decision == TransferConflictDecision.REPLACE) {
            if (destination.isStorage()) trash.put(existing);
            return new ResolvedName(requested, true);
        }
        int dot = requested.lastIndexOf('.');
        String stem = dot > 0 ? requested.substring(0, dot) : requested;
        String suffix = dot > 0 ? requested.substring(dot) : "";
        int sequence = 1;
        String candidate;
        do candidate = stem + " (" + sequence++ + ")" + suffix;
        while (destination.isArchive()
                ? archives.exists(destination, candidate)
                : files.exists(child(destination.directory(), candidate)));
        return new ResolvedName(candidate, false);
    }

    private static String child(String directory, String name) {
        return directory.endsWith("/") ? directory + name : directory + "/" + name;
    }

    private record ResolvedName(String name, boolean replace) {
    }
}
