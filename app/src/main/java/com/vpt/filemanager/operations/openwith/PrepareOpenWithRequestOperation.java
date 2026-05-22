package com.vpt.filemanager.operations.openwith;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vpt.filemanager.format.MimeTypes;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Validate and prepare a system open-with request for one node.
 */
public final class PrepareOpenWithRequestOperation {
    public OpenWithRequest execute(Input input) throws NodeException {
        if (input.node.isFolder()) {
            throw new NodeException("Cannot open folder with another app");
        }
        if (!input.node.path().isLocal()) {
            throw new NodeException("Only local files can be opened with another app");
        }
        String mime = input.mimeOverride == null
                ? MimeTypes.detect(input.node.name())
                : input.mimeOverride;
        return new OpenWithRequest(input.node.path(), mime);
    }

    public static final class Input {
        @NonNull public final VirtualNode node;
        @Nullable public final String mimeOverride;

        public Input(@NonNull VirtualNode node, @Nullable String mimeOverride) {
            this.node = node;
            this.mimeOverride = mimeOverride;
        }
    }
}
