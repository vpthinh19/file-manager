package com.vpt.filemanager.browser.action.entry;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionHandler;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.error.NameConflictException;
import com.vpt.filemanager.browser.item.Path;
import com.vpt.filemanager.data.local.LocalStorageAdapter;
import com.vpt.filemanager.data.archive.ArchiveRepository;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.data.persistence.TrashStore;
import com.vpt.filemanager.browser.workspace.state.PaneState;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class CreateEntryActionHandler implements ActionHandler<CreateEntryAction> {
    private final LocalStorageAdapter files;
    private final ArchiveRepository archives;
    private final TrashStore trash;

    @Inject public CreateEntryActionHandler(LocalStorageAdapter files, ArchiveRepository archives,
                                            TrashStore trash) {
        this.files = files;
        this.archives = archives;
        this.trash = trash;
    }

    @Override
    public ActionResult handle(CreateEntryAction action, WorkspaceSnapshot state)
            throws FileOperationException {
        PaneState pane = state.pane(action.pane());
        Path parent = pane.path;
        if (parent == null || (!parent.isStorage() && !parent.isArchive())) {
            throw new FileOperationException("Items can only be created in storage folders");
        }
        String name = validName(action.name());
        if (parent.isArchive()) {
            if (archives.exists(parent, name)) {
                if (action.policy() == ExistingNamePolicy.FAIL) throw new NameConflictException(name);
                if (action.policy() == ExistingNamePolicy.REPLACE) {
                    String conflictingName = name;
                    Item match = archives.list(parent).stream()
                            .filter(item -> item.name().equals(conflictingName)).findFirst()
                            .orElseThrow(() -> new FileOperationException("Entry disappeared"));
                    archives.delete(java.util.List.of(match));
                } else {
                    name = uniqueArchive(parent, name);
                }
            }
            archives.create(parent, name, action.type() == CreateEntryAction.Type.FOLDER);
            return new ActionResult.RefreshVisible(action.type() == CreateEntryAction.Type.FOLDER
                    ? "Folder created" : "File created");
        }
        String target = child(parent.directory(), name);
        if (files.exists(target)) {
            if (action.policy() == ExistingNamePolicy.FAIL) throw new NameConflictException(name);
            if (action.policy() == ExistingNamePolicy.REPLACE) {
                trash.put(files.inspect(target));
            } else {
                name = unique(parent.directory(), name);
            }
        }
        files.create(parent.directory(), name, action.type() == CreateEntryAction.Type.FOLDER);
        return new ActionResult.RefreshVisible(action.type() == CreateEntryAction.Type.FOLDER
                ? "Folder created" : "File created");
    }

    private String unique(String parent, String requested) {
        int dot = requested.lastIndexOf('.');
        String stem = dot > 0 ? requested.substring(0, dot) : requested;
        String suffix = dot > 0 ? requested.substring(dot) : "";
        int sequence = 1;
        String candidate;
        do candidate = stem + " (" + sequence++ + ")" + suffix;
        while (files.exists(child(parent, candidate)));
        return candidate;
    }

    private String uniqueArchive(Path parent, String requested) throws FileOperationException {
        int dot = requested.lastIndexOf('.');
        String stem = dot > 0 ? requested.substring(0, dot) : requested;
        String suffix = dot > 0 ? requested.substring(dot) : "";
        int sequence = 1;
        String candidate;
        do candidate = stem + " (" + sequence++ + ")" + suffix;
        while (archives.exists(parent, candidate));
        return candidate;
    }

    private static String validName(String raw) throws FileOperationException {
        if (raw == null || raw.isBlank()) throw new FileOperationException("Name is empty");
        String value = raw.trim();
        if (value.contains("/") || value.contains("\\")) throw new FileOperationException("Invalid name");
        return value;
    }

    private static String child(String parent, String name) {
        return parent.endsWith("/") ? parent + name : parent + "/" + name;
    }
}
