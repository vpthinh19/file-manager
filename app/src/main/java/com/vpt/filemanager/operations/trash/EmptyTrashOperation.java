package com.vpt.filemanager.operations.trash;

import androidx.annotation.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.operations.trash.TrashStore;
import com.vpt.filemanager.workspace.MutationResult;

/**
 * Empty all trash entries.
 */
@Singleton
public final class EmptyTrashOperation {
    private final EmptyTrash emptyTrash;

    @Inject
    public EmptyTrashOperation(TrashStore trashStore) {
        this(trashStore::emptyAll);
    }

    public EmptyTrashOperation(EmptyTrash emptyTrash) {
        this.emptyTrash = emptyTrash;
    }

    @NonNull
    public Result execute() throws NodeException {
        emptyTrash.emptyAll();
        return new Result(MutationResult.builder()
                .changedContainer(NodePath.TRASH_ROOT)
                .build());
    }

    @FunctionalInterface
    public interface EmptyTrash {
        void emptyAll() throws NodeException;
    }

    public static final class Result {
        @NonNull public final MutationResult mutation;

        private Result(@NonNull MutationResult mutation) {
            this.mutation = mutation;
        }
    }
}
