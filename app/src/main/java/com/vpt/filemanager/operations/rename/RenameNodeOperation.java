package com.vpt.filemanager.operations.rename;

import androidx.annotation.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.operations.support.NodeFileBackend;

/**
 * Rename one virtual node inside its current parent.
 */
@Singleton
public final class RenameNodeOperation {
    private final NodeFileBackend fileBackend;

    @Inject
    public RenameNodeOperation(NodeFileBackend fileBackend) {
        this.fileBackend = fileBackend;
    }

    @NonNull
    public VirtualNode execute(@NonNull Input input) throws NodeException {
        return fileBackend.rename(input.node, input.newName);
    }

    public static final class Input {
        @NonNull public final VirtualNode node;
        @NonNull public final String newName;

        public Input(@NonNull VirtualNode node, @NonNull String newName) {
            this.node = node;
            this.newName = newName;
        }
    }
}
