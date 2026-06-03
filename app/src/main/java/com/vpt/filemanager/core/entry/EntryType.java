package com.vpt.filemanager.core.entry;

/** Shape of a visible row: up-navigation, folder, or file. Backend identity lives in the path. */
public enum EntryType {
    PARENT,
    FOLDER,
    FILE;

    /** True for rows the user can open as a listing (the parent row and folders). */
    public boolean isFolder() {
        return this != FILE;
    }
}
