package com.vpt.filemanager.operations.create;

/**
 * Policy for create operations when a child with the requested name already exists.
 */
public enum ExistingNamePolicy {
    FAIL,
    REPLACE,
    KEEP_BOTH
}
