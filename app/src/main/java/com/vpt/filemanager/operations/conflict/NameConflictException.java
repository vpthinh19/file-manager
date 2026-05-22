package com.vpt.filemanager.operations.conflict;

import androidx.annotation.NonNull;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Thrown when an operation cannot continue because a sibling with the requested name exists.
 */
public final class NameConflictException extends NodeException {
    private final String name;
    private final VirtualNode existing;

    public NameConflictException(@NonNull String name, @NonNull VirtualNode existing) {
        super("Name already exists: " + name);
        this.name = name;
        this.existing = existing;
    }

    @NonNull
    public String name() {
        return name;
    }

    @NonNull
    public VirtualNode existing() {
        return existing;
    }
}
