package com.vpt.filemanager.resolver;

import com.vpt.filemanager.model.Entry;
import com.vpt.filemanager.model.Location;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class EntryFactory {
    @Inject public EntryFactory() {}

    public Entry physical(File file) {
        return Entry.local(Location.storage(file.getAbsolutePath().replace('\\', '/')),
                file.getName(), file.isDirectory(), file.isDirectory() ? -1L : file.length(),
                file.lastModified());
    }
}
