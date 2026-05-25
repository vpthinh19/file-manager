package com.vpt.filemanager.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/** Immutable row DTO materialized for one currently visible pane. */
public final class Entry {
    private final String key;
    private final String name;
    private final Location location;
    private final EntryKind kind;
    @Nullable private final String localPath;
    @Nullable private final String recordId;
    private final boolean folder;
    private final long size;
    private final long modifiedAt;

    private Entry(String key, String name, Location location, EntryKind kind,
                  @Nullable String localPath, @Nullable String recordId, boolean folder,
                  long size, long modifiedAt) {
        this.key = Objects.requireNonNull(key);
        this.name = Objects.requireNonNull(name);
        this.location = Objects.requireNonNull(location);
        this.kind = Objects.requireNonNull(kind);
        this.localPath = localPath;
        this.recordId = recordId;
        this.folder = folder;
        this.size = size;
        this.modifiedAt = modifiedAt;
    }

    public static Entry parent(Location parent) {
        return new Entry("parent:" + parent.serialize(), "..", parent, EntryKind.PARENT,
                null, null, true, -1L, 0L);
    }

    public static Entry local(Location target, String name, boolean folder, long size, long modifiedAt) {
        return new Entry("local:" + target.serialize(), name, target,
                folder ? EntryKind.FOLDER : EntryKind.FILE, target.physicalPath(), null,
                folder, size, modifiedAt);
    }

    public static Entry archive(Location target, String name, boolean folder, long size, long modifiedAt) {
        return new Entry("archive:" + target.serialize(), name, target, EntryKind.ARCHIVE_ENTRY,
                null, null, folder, size, modifiedAt);
    }

    public static Entry bookmark(Location target, String name, boolean folder, long size, long modifiedAt) {
        return new Entry("bookmark:" + target.serialize(), name, target, EntryKind.BOOKMARK,
                target.physicalPath(), null, folder, size, modifiedAt);
    }

    public static Entry trash(String id, String storedPath, String name, boolean folder,
                              long size, long deletedAt) {
        Location target = Location.storage(storedPath);
        return new Entry("trash:" + id, name, target, EntryKind.TRASH_ITEM, storedPath, id,
                folder, size, deletedAt);
    }

    public String key() { return key; }
    public String name() { return name; }
    public Location location() { return location; }
    public EntryKind kind() { return kind; }
    public boolean isParent() { return kind == EntryKind.PARENT; }
    public boolean isFolder() { return folder; }
    public boolean isArchiveEntry() { return kind == EntryKind.ARCHIVE_ENTRY; }
    public boolean isTrashItem() { return kind == EntryKind.TRASH_ITEM; }
    public long size() { return size; }
    public long modifiedAt() { return modifiedAt; }
    @Nullable public String localPathOrNull() { return localPath; }
    @Nullable public String recordId() { return recordId; }

    @NonNull
    public String localPath() {
        if (localPath == null) throw new IllegalStateException("Entry has no physical path: " + key);
        return localPath;
    }
}
