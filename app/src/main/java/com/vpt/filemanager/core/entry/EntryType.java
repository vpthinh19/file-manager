package com.vpt.filemanager.core.entry;

/**
 * Physical shape of one visible row: the up-navigation row, a folder, or a file.
 *
 * <p>Backend identity (local / archive / trash / bookmarks / search) is NOT
 * encoded here — it comes from the entry's {@link com.vpt.filemanager.core.path.Path}
 * scheme via the {@code StorageRegistry}. This enum only answers "is it a
 * container the user can descend into, or a leaf file?".
 */
public enum EntryType {
    PARENT,
    FOLDER,
    FILE;

    /** True for rows the user can open as a listing (the parent row and folders). */
    public boolean isFolder() {
        return this != FILE;
    }
}
