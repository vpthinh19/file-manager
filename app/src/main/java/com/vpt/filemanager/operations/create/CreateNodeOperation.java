package com.vpt.filemanager.operations.create;

import androidx.annotation.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.operations.support.NodeFileBackend;
import com.vpt.filemanager.operations.conflict.UniqueNameGenerator;
import com.vpt.filemanager.operations.trash.TrashStore;
import com.vpt.filemanager.operations.conflict.NameConflictException;

/**
 * Create a file or folder under a virtual parent node.
 *
 * <p>This operation owns create-specific rules and conflict policy. Android UI decides which
 * policy to request, but creation itself stays independent from fragments, dialogs, toasts, and
 * local filesystem assumptions.
 */
@Singleton
public final class CreateNodeOperation {
    private final NodeFileBackend fileBackend;
    private final TrashStore trashStore;

    @Inject
    public CreateNodeOperation(NodeFileBackend fileBackend, TrashStore trashStore) {
        this.fileBackend = fileBackend;
        this.trashStore = trashStore;
    }

    @NonNull
    public VirtualNode execute(@NonNull Input input) throws NodeException {
        String name = validateName(input.name);
        VirtualNode existing = findChild(input.parent, name);
        String finalName = name;
        if (existing != null) {
            if (input.policy == ExistingNamePolicy.FAIL) {
                throw new NameConflictException(name, existing);
            }
            if (input.policy == ExistingNamePolicy.REPLACE) {
                trashStore.moveToTrash(existing);
            } else if (input.policy == ExistingNamePolicy.KEEP_BOTH) {
                finalName = UniqueNameGenerator.uniqueName(input.parent, name);
            }
        }
        return input.type == CreateNodeType.FOLDER
                ? fileBackend.createFolder(input.parent, finalName)
                : fileBackend.createFile(input.parent, finalName);
    }

    private static String validateName(String name) throws NodeException {
        if (name == null || name.isBlank()) {
            throw new NodeException("Name is empty");
        }
        String trimmed = name.trim();
        if (trimmed.contains("/") || trimmed.contains("\\")) {
            throw new NodeException("Name must not contain path separators: " + trimmed);
        }
        return trimmed;
    }

    private static VirtualNode findChild(VirtualNode parent, String name) throws NodeException {
        for (VirtualNode child : parent.children()) {
            if (child.name().equals(name)) {
                return child;
            }
        }
        return null;
    }

    public static final class Input {
        @NonNull public final VirtualNode parent;
        @NonNull public final CreateNodeType type;
        @NonNull public final String name;
        @NonNull public final ExistingNamePolicy policy;

        public Input(@NonNull VirtualNode parent,
                     @NonNull CreateNodeType type,
                     @NonNull String name,
                     @NonNull ExistingNamePolicy policy) {
            this.parent = parent;
            this.type = type;
            this.name = name;
            this.policy = policy;
        }
    }
}
