package com.vpt.filemanager.entry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vpt.filemanager.navigation.Location;

import java.util.Objects;

/** Immutable ephemeral item rendered in a pane. It is not a retained filesystem tree node. */
public final class Entry {
    private final String key;
    private final String name;
    private final Location location;
    private final EntryType type;
    @Nullable private final String localPath;
    @Nullable private final String recordId;
    private final long size;
    private final long modifiedAt;

    private Entry(String key, String name, Location location, EntryType type,
                  @Nullable String localPath, @Nullable String recordId, long size, long modifiedAt) {
        this.key = Objects.requireNonNull(key);
        this.name = Objects.requireNonNull(name);
        this.location = Objects.requireNonNull(location);
        this.type = Objects.requireNonNull(type);
        this.localPath = localPath;
        this.recordId = recordId;
        this.size = size;
        this.modifiedAt = modifiedAt;
    }

    public static Entry parent(Location parent) {
        return new Entry("parent:" + parent.serialize(), "..", parent, EntryType.PARENT,
                null, null, -1L, 0L);
    }

    public static Entry local(Location target, String physicalPath, String name, boolean folder,
                              long size, long modifiedAt) {
        return new Entry("local:" + target.serialize(), name, target,
                folder ? EntryType.LOCAL_FOLDER : EntryType.LOCAL_FILE, physicalPath, null,
                size, modifiedAt);
    }

    public static Entry archive(Location target, String name, boolean folder, long size, long modifiedAt) {
        return new Entry("archive:" + target.serialize(), name, target,
                folder ? EntryType.ARCHIVE_FOLDER : EntryType.ARCHIVE_FILE,
                null, null, size, modifiedAt);
    }

    public static Entry bookmark(Location target, String physicalPath, String name,
                                 long size, long modifiedAt) {
        return new Entry("bookmark:" + target.serialize(), name, target, EntryType.BOOKMARK_FOLDER,
                physicalPath, null, size, modifiedAt);
    }

    public static Entry trash(String id, String storedPath, String name, boolean folder,
                              long size, long deletedAt) {
        return new Entry("trash:" + id, name, Location.trash(),
                folder ? EntryType.TRASH_FOLDER : EntryType.TRASH_FILE, storedPath, id,
                size, deletedAt);
    }

    public String key() { return key; }
    public String name() { return name; }
    public Location location() { return location; }
    public EntryType type() { return type; }
    public boolean isParent() { return type == EntryType.PARENT; }
    public boolean isFolder() { return type.isFolder(); }
    public boolean isArchiveEntry() { return type.isArchive(); }
    public boolean isTrashItem() { return type.isTrash(); }
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
