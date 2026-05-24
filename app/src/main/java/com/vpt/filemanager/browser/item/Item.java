package com.vpt.filemanager.browser.item;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/** Ephemeral row DTO produced by a fetcher for the current pane listing. */
public final class Item {
    public enum Kind {
        PARENT,
        FILE,
        DIRECTORY,
        ARCHIVE_ENTRY,
        BOOKMARK,
        TRASH
    }

    @NonNull private final String key;
    @NonNull private final String name;
    @Nullable private final String localPath;
    @Nullable private final String recordId;
    @Nullable private final Path target;
    @Nullable private final Path archiveEntry;
    @NonNull private final Kind kind;
    @NonNull private final ItemType behavior;
    private final boolean folder;
    private final long size;
    private final long modifiedAt;

    private Item(String key, String name, @Nullable String localPath,
                 @Nullable String recordId, @Nullable Path target, @Nullable Path archiveEntry, Kind kind,
                 ItemType behavior, boolean folder, long size, long modifiedAt) {
        this.key = Objects.requireNonNull(key, "key");
        this.name = Objects.requireNonNull(name, "name");
        this.localPath = localPath;
        this.recordId = recordId;
        this.target = target;
        this.archiveEntry = archiveEntry;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.behavior = Objects.requireNonNull(behavior, "behavior");
        this.folder = folder;
        this.size = size;
        this.modifiedAt = modifiedAt;
    }

    public static Item parent(Path target) {
        return new Item("parent:" + target.serialize(), "..", null, null, target, null,
                Kind.PARENT, ItemType.PARENT, true, -1, 0);
    }

    public static Item local(String path, String name, boolean folder, long size,
                             long modifiedAt, ItemType behavior) {
        return new Item("local:" + path, name, path, null,
                folder ? Path.storage(path) : null,
                null,
                folder ? Kind.DIRECTORY : Kind.FILE, behavior, folder, size, modifiedAt);
    }

    public static Item bookmark(String path, String name, boolean folder, long size,
                                long modifiedAt, ItemType behavior) {
        return new Item("bookmark:" + path, name, path, null,
                folder ? Path.storage(path) : null, null, Kind.BOOKMARK, behavior, folder,
                size, modifiedAt);
    }

    public static Item archive(Path path, String name, boolean folder, long size,
                               long modifiedAt, ItemType behavior) {
        return new Item("archive:" + path.serialize(), name, null, null,
                folder ? path : null, path, Kind.ARCHIVE_ENTRY, behavior, folder,
                size, modifiedAt);
    }

    public static Item trash(String id, String path, String name, boolean folder,
                             long size, long deletedAt) {
        return new Item("trash:" + id, name, path, id, null, null, Kind.TRASH,
                ItemType.NONE, folder, size, deletedAt);
    }

    @NonNull public String key() { return key; }
    @NonNull public String name() { return name; }
    @NonNull public Kind kind() { return kind; }
    @NonNull public ItemType behavior() { return behavior; }
    public boolean isFolder() { return folder; }
    public boolean isParent() { return kind == Kind.PARENT; }
    public long size() { return size; }
    public long modifiedAt() { return modifiedAt; }
    @Nullable public String localPathOrNull() { return localPath; }
    @Nullable public String recordId() { return recordId; }
    @Nullable public Path target() { return target; }
    @Nullable public Path archiveEntryOrNull() { return archiveEntry; }
    public boolean isArchiveEntry() { return kind == Kind.ARCHIVE_ENTRY; }

    @NonNull
    public String localPath() {
        if (localPath == null) throw new IllegalStateException("Item has no path: " + key);
        return localPath;
    }

    public boolean isLocalActionTarget() {
        return localPath != null && kind != Kind.PARENT && kind != Kind.TRASH;
    }

    @NonNull
    public Path archiveEntry() {
        if (archiveEntry == null) throw new IllegalStateException("Item is not an archive entry: " + key);
        return archiveEntry;
    }
}
