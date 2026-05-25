package com.vpt.filemanager.entry;

/** Source and physical shape of one visible entry. File opening still uses content detection. */
public enum EntryType {
    PARENT(true, false, false),
    LOCAL_FOLDER(true, false, false),
    LOCAL_FILE(false, false, false),
    ARCHIVE_FOLDER(true, true, false),
    ARCHIVE_FILE(false, true, false),
    BOOKMARK_FOLDER(true, false, false),
    TRASH_FOLDER(true, false, true),
    TRASH_FILE(false, false, true);

    private final boolean folder;
    private final boolean archive;
    private final boolean trash;

    EntryType(boolean folder, boolean archive, boolean trash) {
        this.folder = folder;
        this.archive = archive;
        this.trash = trash;
    }

    public boolean isFolder() {
        return folder;
    }

    public boolean isArchive() {
        return archive;
    }

    public boolean isTrash() {
        return trash;
    }
}
