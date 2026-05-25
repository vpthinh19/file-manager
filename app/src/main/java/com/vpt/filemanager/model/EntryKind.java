package com.vpt.filemanager.model;

/** Display and behavior hint for one ephemeral row. Opening still uses content probing. */
public enum EntryKind {
    PARENT,
    FOLDER,
    FILE,
    ARCHIVE_ENTRY,
    TRASH_ITEM,
    BOOKMARK
}
