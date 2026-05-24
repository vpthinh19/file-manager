package com.vpt.filemanager.browser.item;

/** Shared strategy key. Items store this key rather than allocating a handler per row. */
public enum ItemType {
    PARENT,
    FOLDER,
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    ARCHIVE,
    EXTERNAL,
    NONE
}
