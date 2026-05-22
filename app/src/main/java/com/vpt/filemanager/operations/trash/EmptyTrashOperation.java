package com.vpt.filemanager.operations.trash;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.operations.TrashOps;

/**
 * Empty all trash entries.
 */
@Singleton
public final class EmptyTrashOperation {
    private final EmptyTrash emptyTrash;

    @Inject
    public EmptyTrashOperation(TrashOps trashOps) {
        this(trashOps::emptyAll);
    }

    public EmptyTrashOperation(EmptyTrash emptyTrash) {
        this.emptyTrash = emptyTrash;
    }

    public void execute() throws NodeException {
        emptyTrash.emptyAll();
    }

    @FunctionalInterface
    public interface EmptyTrash {
        void emptyAll() throws NodeException;
    }
}
