package com.vpt.filemanager.handler;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.model.Entry;
import com.vpt.filemanager.model.Location;
import com.vpt.filemanager.resolver.EntryFactory;
import com.vpt.filemanager.resolver.ResolveResult;
import com.vpt.filemanager.storage.LocalStorageAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class FolderHandler {
    private final LocalStorageAdapter storage;
    private final EntryFactory entries;

    @Inject
    public FolderHandler(LocalStorageAdapter storage, EntryFactory entries) {
        this.storage = storage;
        this.entries = entries;
    }

    public ResolveResult.Directory open(Location location, File folder) throws FileOperationException {
        List<Entry> result = new ArrayList<>();
        Location parent = location.parent();
        if (parent != null) result.add(Entry.parent(parent));
        for (File child : storage.children(folder)) result.add(entries.physical(child));
        return new ResolveResult.Directory(result);
    }
}
